(ns rb.ynab
  (:require [cheshire.core :as json]
            [clojure.spec.alpha :as s]))


;;; helpers

(defn ->date [timestamp]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (java.util.Date. timestamp)))

(defn ->amount [rands] (int (* 1000 rands)))

;;; Client

(defn wrap-token [request token]
  (assoc-in request [:headers "Authorization"] (str "Bearer " token)))


(defn wrap-endpoint [request]
  (-> request
      (update :url #(str "https://api.youneedabudget.com/v1" %))
      (assoc :insecure? false)))


(defn wrap-json [request]
  (let [has-body? (contains? request :body)
        body-request? (contains? #{:post :put} (:method request))]
    (cond-> request
      true (assoc-in [:headers "Content-Type"] "application/json; charset=utf-8")
      (and body-request? has-body?) (update :body json/generate-string))))


(defn client
  [token http]
  (fn [request]
    (-> request
        (wrap-token token)
        wrap-endpoint
        wrap-json
        http)))


;;; Response


(defn parse-response [response]
  (cond-> response
    (string? (:body response))
    (update :body json/parse-string keyword)
    true
    (select-keys [:status :body :error])))


(defn response-error? [response]
  (contains? response :error))


(defn ->data [response]
  (get-in response [:body :data]))


;;; Api Spec

(s/def ::id #(and (string? %) (> (count %) 0)))
(s/def ::budget_id ::id)
(s/def ::account_id ::id)
(s/def ::date #(and (string? %) (= 10 (count %))))
(s/def ::amount int?)
(s/def ::payee_name ::id)
(s/def ::import_id ::id)
(s/def ::transaction (s/keys :req-un [::account_id
                                      ::date
                                      ::amount
                                      ::payee_name
                                      ::import_id]))
(s/def ::transactions (s/coll-of ::transaction))


;;; Api

(def ^{:doc "The list of budgets"}
  budgets
  {:method :get :url "/budgets"})


(defn accounts
  "Accounts List"
  [budget-id]
  (:pre [(s/valid? ::budget_id budget-id)])
  {:method :get :url (str "/budgets/" budget-id "/accounts")})


(defn ->transaction
  ([account-id date amount payee] (->transaction nil account-id date amount payee))
  ([prefix account-id date amount payee]
   {:post (s/valid? ::transaction %)}
   (let [prefix (if prefix (str "RB" prefix ":") "RB:")]
     {:account_id account-id
      :date date
      :amount amount
      :payee_name payee
      :import_id (str prefix amount ":" date)})))


(defn create-transactions
  "Create a single transaction or multiple transactions"
  [budget-id transactions]
  {:pre [(s/valid? ::budget_id budget-id) (s/valid? ::transactions transactions)]}
  {:method :post :url (str "/budgets/" budget-id "/transactions")
   :body {:transactions transactions}}
  )
