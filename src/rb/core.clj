(ns rb.core
  (:require [rb.ynab :as ynab]
            [rb.ofx :as ofx]
            [rb.fnb :as fnb]
            [rb.tyme :as tyme]
            [rb.history :as history]
            [clj-http.client :as http]
            [me.raynes.fs :as fs]
            [clojure.pprint :as pprint]
            [clojure.java.shell :as sh]))


(defn get-config
  ([] (get-config "config.edn"))
  ([path]
   (-> (read-string (slurp path))
       (update :accounts #(->> %
                               (map (juxt :bank-account-number identity))
                               (into {}))))))


(defn get-token [config] (:ynab-token config))


(defn get-client [config]
  (ynab/client (get-token config)
               (fn [request]
                 (-> request
                     (assoc :throw-exceptions false)
                     http/request
                     ynab/parse-response))))



(defn get-budgets
  "returns a map with budget name as key and ynab budget id as value"
  [config]
  (let [client (get-client config)]
    (->> ynab/budgets
         client
         ynab/->data
         :budgets
         (map (juxt :name :id))
         (into {}))))


(defn get-accounts
  "returns a map with account name as key and ynab account id as value.
   expects budget-id should be set in config"
  [config]
  (let [client (get-client config)
        budget-id (:budget-id config)]
    (->> (ynab/accounts budget-id)
         client
         ynab/->data
         :accounts
         (map (juxt :name :id))
         (into {}))))


(defn process-ofx-zip-transactions
  "Parses all .ofx files contained in .zip files in the resources/downloads folder and returns a list of transactions"
  [config]
  (->> (ofx/process)
       (map (fn [{:keys [amount date-posted id memo account-number]}]
              (let [{:keys [account-id name]} (get-in config [:accounts account-number])]
                (assoc
                 (ynab/->transaction account-id
                                     (ynab/->date date-posted)
                                     (ynab/->amount amount)
                                     memo)
                 :account-name name))))))


(defn push-transactions-to-ynab
  ([config] (push-transactions-to-ynab (get-client config) (process-ofx-zip-transactions config)))
  ([config transactions]
   (let [client (get-client config)
         budget-id (:budget-id config)
         transactions (filter history/new? transactions)]
     (if (empty? transactions)
       {:account-name "No New Transactions" :transactions 0}
       (for [[account-name transactions] (group-by :account-name transactions)]
         (let [response (->> (ynab/create-transactions budget-id transactions)
                             client
                             ynab/parse-response)
               error? (ynab/response-error? response)]
           (when-not error?
             (history/add transactions))
           (merge
            {:account-name account-name
             :transactions (count transactions)}
            (when-not error?
              {:duplicates (count (:duplicate_import_ids (ynab/->data response)))})
            (when error?
              {:error? (get-in response [:body :error])}))))))))

;;; CUSTOM

(defn read-password [prompt]
  (let [p-chars (.readPassword (System/console) "%s" (into-array [prompt]))]
    (apply str p-chars)))


(defn push-tyme-transactions-to-ynab
  ([config]
   (let [{:keys [username]} (:tyme config)
         prompt (str "Password for " username ": ")
         password (read-password prompt)]
     (push-tyme-transactions-to-ynab config password)))
  ([config password]
   (let [{:keys [username every-day goal-save prefix]} (:tyme config)
         transactions (tyme/fetch-transactions prefix username password every-day goal-save)]
     (push-transactions-to-ynab config transactions))))


(defn run-app []
  (let [primary-account (get-config "primary.edn")
        secondary-account (get-config "secondary.edn")
        downloads (:downloads-folder primary-account)
        full (update primary-account :accounts merge (:accounts secondary-account))
        clean #(do
                 (fs/delete-dir (fs/file downloads))
                 (fs/mkdir downloads))
        primary #(fnb/download-ofx primary-account)
        secondary #(fnb/download-ofx secondary-account)
        primary-tyme-ynab #(pprint/pprint (push-tyme-transactions-to-ynab primary-account))
        secondary-tyme-ynab #(pprint/pprint (push-tyme-transactions-to-ynab secondary-account))
        ynab (fn []
               (->> (process-ofx-zip-transactions full)
                    (push-transactions-to-ynab full)
                    (#(do (pprint/pprint %) %))))
        knab #(pprint/pprint (sh/sh "bash" "static.sh" :dir "../ynab/"))
        all #(do
               (clean)
               (primary)
               (secondary)
               (ynab)
               (primary-tyme-ynab)
               (secondary-tyme-ynab))
        fnb-only #(do
                    (clean)
                    (primary)
                    (secondary)
                    (ynab))
        tyme-only #(do
                     (primary-tyme-ynab)
                     (secondary-tyme-ynab))
        get-input #(do (print "> ") (flush) (read-line))]
    (println "** Choose")
    (println "1. Run All")
    (println "2. FNB only")
    (println "3. Tyme Only")
    (println "4. Knab")
    (println "5. Clean")
    (println "6. Primary FNB")
    (println "7. Secondary FNB")
    (println "8. Ynab FNB")
    (println "9. Primary Tyme Ynab")
    (println "10. Secondary Tyme Ynab")

    (loop [k (get-input)]
      (let [r (case k
                "1" (all)
                "5" (clean)
                "6" (primary)
                "7" (secondary)
                "9" (primary-tyme-ynab)
                "10" (secondary-tyme-ynab)
                "8" (ynab)
                "2" (fnb-only)
                "3" (tyme-only)
                "4" (knab)
                "exit")]
        (when (not= "exit" r)
          (recur (get-input)))))))


(defn -main [& args]
  (run-app))


(comment
  (get-accounts (get-config "primary.edn"))

  (fnb/download-ofx (get-config))
  )
