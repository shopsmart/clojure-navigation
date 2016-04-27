(ns bradsdeals.util.inject
  "Note: This namespace is experimental! -- Dave Orme

Inject behavior before, after, or around every form in a block or thread-last form.
Also can convert a block into a vector of 0-arg functions suitable for processing
using map, mapcat, filter, etc.

See: inject, inject->>, fns"
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [icarrp.util.errors :as err]
            [clojure.string :as s])
  (:gen-class))

;; Define the state of the current computation
(defrecord State [])

(defn- pprint-str [src]
  (with-out-str (clojure.pprint/pprint src)))


(defn arity
 "Returns the maximum parameter count of each invoke method found by reflection
  on the input instance. The returned value can be then interpreted as the arity
  of the input function. The count does NOT detect variadic functions."
  [f]
  (let [invokes (filter #(= "invoke" (.getName %1)) (.getDeclaredMethods (class f)))]
    (apply max (map #(alength (.getParameterTypes %1)) invokes))))


(defn run-f
  [f wrapped-f src state]
  (let [state-with-src (assoc state :src src)
        n-args (arity f)
        result (cond (= n-args 3) (f wrapped-f (:value state) state-with-src)
                     (= n-args 2) (f wrapped-f (:value state))
                     :else (throw (IllegalArgumentException. (str "Expected arity 2 or 3 but got " n-args))))]
    (if (instance? State result)
      result
      (assoc state-with-src :value result))))


(defmacro inject->>
  "A threading macro that passes each form in body to f, optionally along
with a map that may contain state about the calculation in progress.  The
injected function f may add behavior before invoking the passed form, after
invoking the passed form, or may take the results of invoking the passed form
and further process it.  f must be a function of either the form
[wrapped-fn fn-arg] or [wrapped-fn fn-arg state] where wrapped-fn is the
body form to be evaluated wrapped in a function of 1 argument.  fn-arg is
the argument that must be passed to wrapped-fn.  state is the calculation
state map.  Since f might perform logging behavior, the :src key inside
state contains the unevaluated source code of wrapped-fn."
  [f initial-val & body]
  (let [body (for [func body] `(run-f ~f (partial ~@func) (quote ~func)))]
    `(let [result# (->> (run-f ~f (fn [val#] ~initial-val) (quote ~initial-val) (State.))
                        ~@body)]
       (if (contains? result# :error)
         result#
         (:value result#)))))


(defmacro inject
  "A macro similar to 'do' that passes each form in body to f, optionally along
with a map that may contain state about the calculation in progress.  The
injected function f may add behavior before invoking the passed form, after
invoking the passed form, or may take the results of invoking the passed form
and further process it.  f must be a function with the argument list
[wrapped-fn fn-arg] or [wrapped-fn fn-arg state] where wrapped-fn is the
body form to be evaluated wrapped in a function of 1 argument.  fn-arg is
the argument that must be passed to wrapped-fn.  state is the calculation
state map.  Since f might perform logging behavior, the :src key inside
state contains the unevaluated source code of wrapped-fn."
  [f & body]
  (let [body (for [func body] `(run-f ~f (fn [val#] ~func) (quote ~func)))]
    `(let [result# (->> (State.)
                        ~@body)]
       (if (contains? result# :error)
         result#
         (:value result#)))))


(defmacro fns
  "Transform forms in body into a vector of functions suitable for processing
using map, mapcat, reduce, etc."
  [& body]
  (let [body (vec (for [f body] `(fn [] ~f)))]
    `~body))


;; Utility methods for processing the state map in injected functions

(defn error
  "Set an error state.  The :error key will be set to err and any :value
key will be removed."
  [err computation-state]
    (dissoc (assoc computation-state
                   :error err)
                   :value))

(defn halt
  "Set the :halted key to true.  This halts further processing.  If an
:error state is set, the entire computation-state will be returned, otherwise
the :value will be returned."
  [computation-state]
  (assoc computation-state :halted true))

(defn fatal-error
  "Set an :error state and the :halted state at the same time."
  [err computation-state]
  (halt (error computation-state err)))

(defn save-time
  "Set :time-millis to the value in the parameter time-millis."
  [time-millis computation-state]
  (assoc computation-state :time-millis time-millis))

(defn save-result
  "Sets :value to result."
  [result computation-state]
  (assoc computation-state :value result))

(defmacro time-do
  "Times how long it takes to evaluate body and returns the result in a
vector of the form [result elapsed-time].  elapsed-time is in milliseconds."
  [& body]
  `(let [start-time# (System/currentTimeMillis)
        result# (do ~@body)
        elapsed-time# (- (System/currentTimeMillis) start-time#)]
    [result# elapsed-time#]))


(defn halt-on-error
  "If the state contains an :error, set the :halted property, which will
stop evaluating further expressions and return the current error state."
  [computation-state]
  (if (contains? computation-state :error)
    (halt computation-state)
    computation-state))


(defn throw-error
  "If the state contains an :error that is a Java Throwable class, throw
the Throwable.  Otherwise continue processing."
  [computation-state]
  (if (instance? Throwable (:error computation-state))
    (throw (:error computation-state))
    computation-state))


(defn log-err
  "If the state's :value is a Java Throwable, log the :value as an error.
If the state contains an :error, log that in a manner appropriate to its type."
  [src computation-state]
  (letfn [(log-err [err]
            (if (instance? Throwable err)
              (log/error err (str src " : " (.getMessage err)))
              (log/error (str src " : " err))))]

    (if (instance? Throwable (:value computation-state))
      (log-err (:value computation-state)))

    (if (contains? computation-state :error)
      (let [err (:error computation-state)]
        (log-err err))))
  computation-state)


(defn log-timing
  "If time-millis is higher than threshold, then log the elapsed time
along with the source code line that ran slower than threshold."
  [src time-millis threshold computation-state]
  (let [elapsed-time (double (/ time-millis 1000))]
    (if (> elapsed-time threshold)
      (log/debug (format "%s : %.3f seconds" (str src) elapsed-time))))
  computation-state)


(defn detect-error
  "Detects error values according to the ComputationFailed
protocol.

Clients can extend the ComputationFailed protocol to create
their own error objects that are not exceptions.  For
example, form validation error objects would not normally
be exceptions but could be expressed and processed in this
way."
  [result state]
  (if-not (contains? state :error)
    (if (err/failure? result)
      (error result state)
      state)
    state))


;;;; Predefined behaviors for use with the inject macros

(defn logging
  "Log each form as it is executed at debug log level.  If execution time is
greater than 1/2 second, log the execution time as well.

Usage: (inject logging (form1) (form2) ...)"
  [f val state]
  (let [src (:src state)]
    (log/debug (str src))

    (let [[result time-millis] (time-do (err/try* (f val)))]
      (->> state
           (save-time time-millis)
           (save-result result)
           (log-timing src time-millis 0.5)
           (detect-error result)
           (log-err src)
           (halt-on-error)
           (throw-error)))))


(defn trace
  "pprint a trace of each form as it is executed to *out* along with
each form's resulting value(s).  Not recommended to use when
processing large data sets.

Usage: (inject trace (form1) (form2) ...)"
  [f val state]
  (let [src (:src state)]
    (pprint src)
    (let [new-val (f val)]
      (print "==> ")
      (pprint new-val)
      new-val)))


(defn trace-log
  "pprint a trace of each form as it is executed to the log along with
each form's resulting value(s).  Not recommended to use when
processing large data sets.

Usage: (inject trace-log (form1) (form2) ...)"
  [f val state]
  (let [src (:src state)]
    (log/debug (s/trim (pprint-str src)))
    (let [new-val (f val)]
      (log/debug
       (s/trim (str (pprint-str src) " ==> " (pprint-str new-val))))
      new-val)))


(defn trace-return
  "Changes the return value of a block to be a vector where the initial
element is the block's actual result and the next element is a trace of
the block's execution.  If a trace returns large amounts of data, this
form is preferred because these results can then be searched and filtered
using the usual tools.

Usage: (inject trace-return (form1) (form2) ...)"
  [f val state]
  (let [src (:src state)
        src-fn-name (keyword (str (if (and (seq? src) (not (string? src))) (first src) src) "->"))
        f-input (first val)
        result (f f-input)
        trace (assoc (err/replace-nil (second val) {}) src-fn-name result)]
    [result trace]))
