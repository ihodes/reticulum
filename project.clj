(defproject io.isaachodes/reticulum "0.0.1-SNAPSHOT"
  :description "Extensible extended finite state machines."
  :dependencies [[org.clojure/clojure "1.5.1"]

                 [cheshire "5.2.0"]

                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]
                 [org.clojure/core.logic "0.8.5"]

                 [prismatic/schema "0.1.9"]
                 [prismatic/plumbing "0.1.1"]]
  :plugins [[lein-marginalia "0.7.1"]])
