(ns reticulum.validation
  (:require [schema.core :as sc]))



(def ^{:private true}
  Actions
  {(sc/optional-key :exit)  [(sc/pred fn? 'fn?)]
   (sc/optional-key :enter) [(sc/pred fn? 'fn?)]
   (sc/optional-key :event) [(sc/pred fn? 'fn?)]})

(def ^{:private true}
  State
  (sc/if #(or (contains? % :states) (contains? % :initial))
    {(sc/required-key :initial)  sc/String
     (sc/required-key :states)   [(sc/recursive #'State)]
     (sc/required-key :name)     sc/String
     (sc/optional-key :actions)  Actions}
    
    {(sc/required-key :name)    sc/String
     (sc/optional-key :actions)  Actions}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;; ## Validation API

(defn validate-syntactically-correct-state
  "Verifies that the `candidate` FSM is syntactically correct. 

   Throws if invalid, returns the FSM if valid."
  [candidate]
  (sc/validate State candidate))

(defn syntactically-correct-state?
  "True if candidate is a valid FSM, else false."
  [candidate]
  (try (validate-syntactically-correct-state candidate)
       true
       (catch Exception e false)))

