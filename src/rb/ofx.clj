(ns rb.ofx
  (:require [clojure.string :as string]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :as compression]
            [ofx-clj.core :as ofx]))


(def download-path "resources/downloads")
(def zip-e ".zip")
(def ofx-e ".ofx")

;; utils

(defn index-by [k coll]
  (->> coll
       (map (juxt k identity))
       (into {})))


;; file helpers

(defn get-files-of-type [type dir]
  (->> (fs/file dir)
       file-seq
       (filter #(and (fs/file? %) (= (fs/extension %) type)))))


;; ofx

(defn message->transactions [message]
  (let [transactions (get-in message [:message :transaction-list :transactions])
        account-number (:account-number (get-in message [:message :account]))]
    (->> transactions
         (map #(-> %
                   (select-keys [:amount :id :date-posted :memo])
                   (assoc :account-number account-number))))))


(defn ofx->transactions [ofx-data]
  (let [banking-data (get (index-by :type ofx-data) "banking") ]
    (->> banking-data
         :messages
         (map message->transactions)
         (reduce into))))


(defn ofx-file->transactions [file]
  (->> file
       ofx/parse
       ofx->transactions))

;; extract transactions from zip

(defn process-zip [file]
  (let [target-dir (str download-path "/" (fs/name file))]
    (compression/unzip file target-dir)
    (let [transactions
          (->> (get-files-of-type ofx-e target-dir)
               (map ofx-file->transactions)
               (reduce into)
               doall)]
      (fs/delete-dir target-dir)
      transactions)))


(defn process-zips [files]
  (->> files
       (map process-zip)
       (reduce into)))


(defn process []
  (->> (get-files-of-type zip-e download-path)
       (process-zips)))


(comment
  (let [files (get-files-of-type zip-e download-path)]
    (->> files
         (process-zips)
         (group-by :account-number)
         (map (fn [[k t]] [k (count t)]))))


  )
