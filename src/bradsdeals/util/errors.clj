(ns bradsdeals.util.errors
  (:require [clojure.tools.logging :as log])
  (:gen-class))


(defn replace-nil
  [maybe-nil replacement]
  (if (nil? maybe-nil)
    replacement
    maybe-nil))


(defn not-nil [value name]
  (if (nil? value)
    (throw (java.lang.IllegalArgumentException. (str name " cannot be nil")))
    value))

(defprotocol ComputationFailed
  "A protocol that determines if a computation has resulted in a failure.
   This allows the definition of what constitutes a failure to be extended
   to new types by the consumer."
  (failure? [self]))

(extend-protocol ComputationFailed
  Object
  (failure? [self] false)

  Throwable
  (failure? [self] true)

  nil
  (failure? [self] false))


(defmacro try*
  "A variant of try that translates exceptions into return values or a
specified default value"
  ([body]
   `(try ~body (catch Throwable e# e#)))
  ([body default-value-if-failure]
   `(try ~body (catch Throwable e# ~default-value-if-failure))))


(defn retry*
  "Retry calling the specified function f & args while pausing pause-millis
between attempts.  Throwable objects, and uncaught exceptions are all
considered errors.  After tries attempts, the last error is returned."
  [tries pause-millis f & args]
  (let [res (try* (apply f args))]
    (if (not (failure? res))
      res
      (if (= 0 tries)
        res
        (do
          (if (instance? Throwable res)
            (log/error res "A failure occurred; retrying...")
            (log/error (str "A failure occurred; retrying...  [" (pr-str res) "]")))
          (Thread/sleep pause-millis)
          (recur (dec tries) pause-millis f args))))))

(defn retry
  "Retry calling the specified function f & args while pausing pause-millis
between attempts.  Uncaught exceptions are considered errors.  After tries
attempts, the last caught exception is re-thrown."
  [tries pause-millis f & args]
  (let [res (try {:value (apply f args)}
                 (catch Exception e
                   (if (= 0 tries)
                     (throw e)
                     {:exception e})))]
    (if (:exception res)
      (do
        (log/error (:exception res) "A failure occurred; retrying...")
        (Thread/sleep pause-millis)
        (recur (dec tries) pause-millis f args))
      (:value res))))


(defn retry-statements
  [tries pause-millis]
  (fn [f val] (retry tries pause-millis f val)))

