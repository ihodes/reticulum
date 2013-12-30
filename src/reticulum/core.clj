(ns reticulum.core
  "This namespace exports functions for sending events to asynchronous
   extended finite state machines. They mostly conform to the UML extended FSM
   spec, with small deviations for what I percieve to be an easier time
   reasoning about them.

   More information can be found in /docs/FSM.md.
   "
  (:require [reticulum.tree-nav :refer :all]
            [clojure.core.async :refer [<! >! >!! <!! chan go go-loop]]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;; ## Utilities
(defn get-state-with
  "Finds single state within a FSM using breadth-first-search by matching on
   predicate."
  [pred? fsm]
  (first (breadth-first-search pred? :states fsm)))

(defn name=?
  "Predicate for use with breadth-first-search for finding a state by name
   within a FSM."
  [name]
  (comp #{name} :name))

(defn state-in?
  "True if the state with name `state-name` is in the `fsm`."
  [state-name fsm]
  (get-state-with (name=? state-name) fsm))

(defn get-initial-state-for
  "Given a state, returns the innermost initial state belonging to it, if any."
  [state]
  (loop [state state]
    (if (:initial state)
      (recur (get-state-with (name=? (:initial state)) state))
      state)))
 

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;; ## Execution implementation, plumbing.
(defn- execute-action!
  "Executes the given actions with the given state. Returns updated state.

   State is a map of the form {:current-state-name String, :context Map}."
  [{:keys [current-state-name] :as context} action out]
  (letfn [(return  ([] out)
                   ([cont context] (go (>! out [cont context]))))]
    (action context return)))

(declare transition!)
(defn- execute-event-sequence!
  "Asynchronously sxecutes actions in action-seq, put!ing the final context value
   on the out port when all actions have been processed (or short-circuited).
   Transitions to new states, executing all pertinent exit and enter actions as
   necessary."
  [fsm {:keys [current-state-name] :as context} event-seq out]
  (let [chain (chan 1)]
    (if-not (state-in? current-state-name fsm)
      (throw (Exception. (str "State named \"" current-state-name "\""
                              " does not exist within this FSM: "
                              "invalid transition."))))
    (>!! chain [true context])
    (go-loop [[continue? ctx] (<! chain), events event-seq, old-ctx context]
             (cond (apply (comp not =) (map :current-state-name [ctx old-ctx]))
                   (transition! fsm ctx (:current-state-name old-ctx) out)
                   (or (not continue?) (empty? events))
                   (>! out ctx)
                   :else
                   (do (execute-action! ctx (first events) chain)
                       (recur (<! chain) (rest events) ctx))))
    out))

(defn- execute-exit-enter-sequence!
  "Asynchronously sxecutes actions in action-seq, put!ing the final context value
   on the out port when all actions have been processed (or short-circuited)."
  [fsm {:keys [current-state-name] :as context} action-seq out]
  (let [chain (chan 1)]
    (>!! chain [true context])
    (go-loop [[continue? ctx] (<! chain), actions action-seq]
             (if (or (not continue?) (empty? actions))  (>! out ctx)
               (do (execute-action! ctx (first actions) chain)
                   (recur (<! chain) (rest actions)))))
    out))

(defn- transition!
  "Determines the exit and enter action sequences which should be executed upon
   transition from the `old-state-name` to the current state described within
   the `context`."
  [fsm context old-state-name out]
  (let [target        (get-initial-state-for
                       (get-state-with (name=? (:current-state-name context)) fsm))
        context       (assoc context :current-state-name (:name target))
        source?       (name=? old-state-name)
        target?       (name=? (:name target))
        paths         (paths-between source? target? :states fsm)
        exiting       (reverse (rest (:i-to-p1 paths)))
        entering      (rest (:i-to-p2 paths))
        exit-actions  (mapcat #(get-in % [:actions :exit] []) exiting)
        enter-actions (mapcat #(get-in % [:actions :enter] []) entering)
        actions       (concat exit-actions enter-actions)]
    (execute-exit-enter-sequence! fsm context actions out)
    out))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;; ## FSM API
(defn initialize!
  "Transitions the FSM through the root state to the initial state, returning
   the modified context and executing all enter actions along the way (including
   those, if any, of the root state."
  ([fsm] (initialize! fsm {}))
  ([fsm context]
     (let [init-state-name (:name (get-initial-state-for fsm))
           init-name?      (comp #{init-state-name} :name)
           root-to-init    (path-to init-name? :states fsm)
           actions         (mapcat #(get-in % [:actions :enter] []) root-to-init)
           out             (chan)
           context         (assoc context :current-state-name init-state-name)]
       (execute-event-sequence! fsm context actions out)))
  ([fsm context synchronous?] (<!! (initialize! fsm context))))

(defn send!
  "Sends the event and associated arguments to the FSM instance specified by 
   context, including key current-state-name, designating the current state
   the machine is in), and any other keyvals.

   Returns an unbuffered channel which will contains a map containing the
   current-state-name (new or the same), the event (in the form 
   {:name string-name-of-event :args {...}}) and whatever other values actions
    may have added or modified after the event is fully processed.

   A synchronous version of send! may be used; simply passing a fourth parameter
   will cause send! to block, eventually returning the aforementioned context map
   in lieu of immediately returning the context map."
  ([fsm {:keys [current-state-name contexts] :as context} event]
     (let [curr-name?   (comp #{current-state-name} :name)
           curr-to-root (reverse (path-to curr-name? :states fsm))
           events       (mapcat #(get-in % [:actions :event] []) curr-to-root)
           context      (assoc context :event event)
           out          (chan)]
       (execute-event-sequence! fsm context events out)))
  ([fsm context event synchronous?] (<!! (send! fsm context event))))
