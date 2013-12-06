(ns reticulum.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [>!! <!!]]
            [reticulum.core :refer :all]))



(declare trans-fsm)
(deftest transition
  (testing "Ensure that the FSM transitions when it should."
    (let [context   {:current-state-name "A"}
          new-state (<!! (send! trans-fsm context {:name "Boo!"}))]
      (is (= "D" (:current-state-name new-state))))))

(declare context-fsm)
(deftest context-update
  (testing "Ensure that the context updates when it should."
    (let [context   {:current-state-name "A"}
          new-state (<!! (send! context-fsm context {:name "Boo!"}))]
      (is (= "Event in Root" (:root-string new-state)))
      (is (= "Exited A" (:a-string new-state)))
      (is (= "Entered C" (:c-string new-state)))
      (is (= "Entered D" (:d-string new-state))))))

(declare ss-fsm)
(deftest short-circuit
  (testing "Ensure that an event can correctly short-circuit."
    (let [context   {:current-state-name "A"}
          new-state (<!! (send! ss-fsm context {:name "Boo!"}))]
      (is (nil? (:ERROR new-state)))
      (is (= 2 (:number-of-events new-state))))))

(declare order-fsm)
(deftest event-order
  (testing "Ensure that the events execute in the correct order."
    (let [context   {:current-state-name "Root"}
          new-state (<!! (send! order-fsm context {:name "Boo!"}))]
        (is (= [1, 2] (:events new-state))))))

(declare order-exit-enter-fsm)
(deftest exit-enter-order
  (testing "Ensure that the exit & enter events execute in the correct order."
    (let [context   {:current-state-name "A"}
          new-state (<!! (send! order-exit-enter-fsm context {:name "Boo!"}))]
        (is (= ["Exited A" "Entered C" "Entered D"] (:events new-state))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;; Test FSMs
(def trans-fsm 
  {:name "Root" :initial "A" :states [{:name "A"} {:name "C" :initial "D"
                                                   :states [{:name "D"}]}]
   :actions {:event [(fn [state return]
                       (return true (assoc state :current-state-name "C")))]}})

(def context-fsm 
  {:name "Root"
   :initial "A"
   :states [{:name "A"
             :actions {:exit [#(%2 true (merge %1 {:a-string "Exited A"}))]}}
            {:name "C"
             :initial "D"
             :actions {:enter
                       [#(%2 true (merge %1 {:c-string "Entered C"}))]}
             :states [{:name "D"
                       :actions {:enter
                                 [#(%2 true (merge %1 {:d-string "Entered D"}))]}}]}]
   :actions {:event [(fn [state return]
                       (return true (merge state {:root-string "Event in Root"
                                                  :current-state-name "C"})))]}})

(def ss-fsm 
  {:name "Root" :initial "A"
   :states [{:name "A"}
            {:name "C"
             :initial "D"
             :actions {:enter [#(%2 false (update-in %1 [:number-of-events] inc))]}
             :states [{:name "D"
                       ;; shouldn't reach this; enter action in C ss'd rest of actions
                       :actions {:enter [#(%2 true (update-in %1 [:number-of-events] inc))]}}]}]
   :actions {:event [(fn [state return]
                       (return true
                               (merge state {:number-of-events 1
                                             :current-state-name "C"})))
                     (fn [state return]
                       (return true ;; should not reach this; transition ss's remaining
                                    ;; events
                               (merge state {:ERROR true})))]}})

(def order-fsm
  {:name "Root"
   :actions {:event [#(%2 true (assoc %1 :events [1]))
                     #(%2 true (update-in %1 [:events] conj 2))]}})

(def order-exit-enter-fsm 
  {:name "Root"
   :initial "A"
   :states [{:name "A"
             :actions {:exit [#(%2 true (update-in %1 [:events] conj "Exited A"))]}}
            {:name "C"
             :initial "D"
             :actions {:enter
                       [#(%2 true (update-in %1 [:events] conj "Entered C"))]}
             :states [{:name "D"
                       :actions {:enter
                                 [#(%2 true (update-in %1 [:events] conj "Entered D"))]}}]}]
   :actions {:event [#(%2 true (assoc % :current-state-name "C" :events []))]}})
