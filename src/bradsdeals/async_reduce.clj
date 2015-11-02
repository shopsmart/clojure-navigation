(ns bradsdeals.async-reduce
  "Run a reduce operation where each step takes a long time in the background.
Provide a Future for returning a result and a function for finding out the
current status of the reduce."
  (:require [okku.core :refer :all])
  (:import [java.util UUID]
           [akka.actor ActorRef]
           [akka.pattern Patterns]
           [scala.concurrent Await]
           [scala.concurrent.duration Duration]
           [java.util.concurrent TimeUnit])
  (:gen-class))



(defn- ask
  "Use the Akka ask pattern. Returns a future object
which can be waited on by calling 'wait'.  timeout is
in milliseconds"
  [^ActorRef actor msg timeout]
     (Patterns/ask actor msg timeout))

(defn- wait
  "Wait for a Scala Future to complete and return its result."
  ([future]
     (Await/result future (Duration/Inf)))
  ([future duration]
     (Await/result future (Duration/create
			   (:value duration)
			   (:unit duration)))))



(defn- start-system []
  (actor-system (str "reducer-" (.toString (UUID/randomUUID)))))

(defn- stop-system [actor-system]
  (.shutdown actor-system))


(defn- reduce-step-actor [reducer-fn actor-system]
  (spawn (actor (onReceive [[cur-result nextval]]
                            (! {:message :step-result :value (reducer-fn cur-result nextval)})))
         :in actor-system))


(defn- reducer-factory
  [reducer-fn start-value collection]
    (let [actors (start-system)
          reduce-step (reduce-step-actor reducer-fn actors)
          state (atom ::none)
          coll (atom collection)
          result (promise)]
      (fn []
        (let [reduction-actor
              (spawn
                 (actor
                   (onReceive [{m :message v :value}]
                              (case m
                                :start (if-not (empty? @coll)
                                         (if (= ::none start-value)
                                           (let [start-value (first @coll)
                                                 next-value (second @coll)]
                                             (swap! coll rest)
                                             (swap! coll rest)
                                             (! reduce-step [start-value next-value]))
                                           (let [next-value (first @coll)]
                                             (swap! coll rest)
                                             (! reduce-step [start-value next-value]))))
                                :step-result (do
                                               (swap! state (fn [_] v))
                                               (if-not (empty? @coll)
                                                 (let [next-val (first @coll)]
                                                   (swap! coll rest)
                                                   (! reduce-step [@state next-val]))
                                                 (do
                                                   (deliver result @state)
                                                   (.stop reduce-step)
                                                   (stop-system actors))))
                                :status (! {:state @state :work-remaining @coll :completed (clojure.core/realized? result)}))))
                 :in actors)
              reducer {:result result
                       :actor-system actors
                       :actor reduction-actor}]
          (.tell reduction-actor {:message :start} reduction-actor)
          reducer))))


(defn reduce
  "A reduce function for when each step in the reduction takes a long time, needs
to run in the background, and access to intermediate results are desired.
With the exception of not supporting reduced? for returning partial results,
stepwise-reduce follows the Clojure contract for the reduce function.

stepwise-reduce returns a reduction, which is to treated as an opaque type.
Ask a reduction for the current computation status using the status function.
The realized? function treats the reduction like a promise and returns if
the reduction has achieved a final result.  The current-result function
returns the final result if it is realized, or the current status of the
reduction if it is not.  The result function returns a Clojure Promise, that
can be dereferenced in the usual way to block until the final result is
realized."
  ([reducer-fn collection]
    (stepwise-reduce reducer-fn ::none collection))
  ([reducer-fn start-value collection]
    (let [start-reduce-fn (reducer-factory reducer-fn start-value collection)]
      (start-reduce-fn))))


(defn realized?
  "Return true if the reduction is complete and false otherwise."
  [reduction]
  (clojure.core/realized? (:result reduction)))


(defn status
  "Return the status of a reduction as a map with the current :state mapped
to the most recent intermediate value and :work-remaining mapped to the
remaining collection elements to be processed."
  ([reduction]
    (status reduction 2000))
  ([reduction timeout]
    (if (realized? reduction)
      {:state @(:result reduction) :work-remaining [] :completed true}
      (wait (ask (:actor reduction) {:message :status} timeout)))))


(defn result
  "Return a Clojure Promise that can be dereferenced to obtain the result of
the reduction when it is complete."
  [reduction]
  (:result reduction))

