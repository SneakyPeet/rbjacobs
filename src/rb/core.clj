(ns rb.core
  (:require [rb.ynab :as ynab]
            [rb.ofx :as ofx]
            [rb.fnb :as fnb]
            [clj-http.client :as http]
            [me.raynes.fs :as fs]))


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
         budget-id (:budget-id config)]
     (for [[account-name transactions] (group-by :account-name transactions)]
       (let [response (->> (ynab/create-transactions budget-id transactions)
                           client
                           ynab/parse-response)
             error? (ynab/response-error? response)]
         (merge
          {:account-name account-name
           :transactions (count transactions)}
          (when-not error?
            {:duplicates (count (:duplicate_import_ids (ynab/->data response)))})
          (when error?
            {:error? (get-in response [:body :error])})))))))

;;; CUSTOM


(defn run-example []
  (let [primary-account (get-config "primary.edn")
        secondary-account (get-config "secondary.edn")
        downloads (:downloads-folder primary-account)
        full (update primary-account :accounts merge (:accounts secondary-account))]
    (fs/delete-dir (fs/file downloads))
    (fs/mkdir downloads)
    (fnb/download-ofx primary-account)
    (fnb/download-ofx secondary-account)
    (->> (process-ofx-zip-transactions full)
         (push-transactions-to-ynab full)
         (#(do (prn %) %)))))


(defn -main [& args]
  (run-example))


(comment

  (fnb/download-ofx (get-config))
  )
