(ns rb.core
  (:require [rb.ynab :as ynab]
            [rb.ofx :as ofx]
            [clj-http.client :as http]))


(defn get-token [] (slurp "secret.txt"))


(defn get-client []
  (ynab/client (get-token)
               (fn [request]
                 (-> request
                     (assoc :throw-exceptions false)
                     http/request
                     ynab/parse-response))))


(defn get-config []
  (-> (read-string (slurp "config.edn"))
      (update :accounts #(->> %
                              (map (juxt :bank-account-number identity))
                              (into {})))))


(defn get-budgets
  "returns a map with budget name as key and ynab budget id as value"
  []
  (let [client (get-client)]
    (->> ynab/budgets
         client
         ynab/->data
         :budgets
         (map (juxt :name :id))
         (into {}))))


(defn get-accounts
  "returns a map with account name as key and ynab account id as value.
   expects budget-id should be set in config"
  ([] (get-accounts (get-config)))
  ([config]
   (let [client (get-client)
         budget-id (:budget-id config)]
     (->> (ynab/accounts budget-id)
          client
          ynab/->data
          :accounts
          (map (juxt :name :id))
          (into {})))))


(defn process-ofx-zip-transactions
  "Parses all .ofx files contained in .zip files in the resources/downloads folder and returns a list of transactions"
  ([] (process-ofx-zip-transactions (get-config)))
  ([config]
   (->> (ofx/process)
        (map (fn [{:keys [amount date-posted id memo account-number]}]
               (let [{:keys [account-id name]} (get-in config [:accounts account-number])]
                 (assoc
                  (ynab/->transaction account-id
                                      (ynab/->date date-posted)
                                      (ynab/->amount amount)
                                      memo)
                  :account-name name)))))))


(defn push-transactions-to-ynab
  ([] (push-transactions-to-ynab (get-client) (process-ofx-zip-transactions)))
  ([transactions] (push-transactions-to-ynab (get-client) transactions))
  ([client transactions]
   (let [budget-id (:budget-id (get-config))]
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


(comment
  (->> (process-ofx-zip-transactions)
       push-transactions-to-ynab)
  )
