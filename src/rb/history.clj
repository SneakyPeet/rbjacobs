(ns rb.history
  (:require [clojure.java.io :as io]))

(def ^:private history-path "history.edn")

(defn- ensure-file-exists []
  (let [history-file (io/file history-path)]
    (when-not (.exists history-file)
      (let [l *print-length*]
        (set! *print-length* Integer/MAX_VALUE)
        (spit history-file (pr-str #{}))
        (set! *print-length* l)))))


(defn- get-existing-history []
  (ensure-file-exists)
  (read-string (slurp history-path)))


(def ^:private *history (atom (get-existing-history)))

(add-watch *history :write-to-file
           (fn [_ _ _ state]
             (spit history-path (pr-str state))))


(defn new? [transaction]
  (not (contains? @*history (:import_id transaction))))


(defn add [transactions]
  (swap! *history
         into
         (map :import_id transactions)))
