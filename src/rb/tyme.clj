(ns rb.tyme
  (:require [etaoin.api :as e]
            [clojure.string :as string]
            [hickory.core :as hickory]
            [hickory.select :as s]
            [rb.ynab :as ynab]))

(def ^:private account-button-query {:tag :div :role "button" :tabindex "0" :class "featured-list-item__link"})
(def ^:private transaction-row {:tag :div :class "row display-table transaction-row hidden-xs"})


(defn- driver [download-dir] (e/chrome {:download-dir download-dir}))


(defn- login [driver username password]
  (doto driver
    (e/go "https://bank.tymedigital.co.za/")
    (e/fill {:tag :input :autocomplete :username} username)
    (e/fill {:tag :input :autocomplete :current-password} password)
    (e/click {:tag :button :type "button" :class "btn btn-yellow btn-block"})
    (e/wait-visible account-button-query)))


(defn- route-everyday-account [driver]
  (doto driver
    (e/click-el (first (e/query-all driver account-button-query)))
    (e/wait-visible transaction-row)))


(defn- route-goal-saves [driver]
  (doto driver
    (e/click-el (second (e/query-all driver account-button-query)))
    (e/wait-visible {:tag :div :class "goal-save-info container"})))


(defn- route-home [driver]
  (doto driver
    (e/click {:tag :a :href "/"})
    (e/wait-visible account-button-query)))


(def ^:private date-from (java.text.SimpleDateFormat. "dd MMM yyyy HH"))

(defn- parse-date [d] (ynab/->date (.getTime (.parse date-from (str d " 14")))))


(defn- parse-transaction-row [html]
  (let [data (hickory/as-hickory (hickory/parse html))]
    {:date (-> (s/select (s/class "date") data)
               first :content first :content first
               parse-date)
     :description (-> (s/select (s/class "description") data)
                      first :content first :content first
                      string/trim)
     :amount (-> (s/select (s/class "amount") data) first :content first
                 (string/replace #"R" "") (string/replace #" " "")
                 read-string)}))


(defn- parse-transaction-table [driver]
  (->> (e/query-all driver transaction-row)
       (map (fn [el]
              (->
               (e/get-element-inner-html-el driver el)
               parse-transaction-row)))))


(comment
  (def driver (e/chrome))
  (def username "")
  (def password "")

  (doto driver
    (login username password)
    (route-everyday-account))

  (parse-transaction-table driver)




  )
