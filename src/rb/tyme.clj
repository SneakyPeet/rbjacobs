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
                 read-string
                 ynab/->amount)}))


(defn- parse-transaction-table [driver]
  (->> (e/query-all driver transaction-row)
       (map (fn [el]
              (->
               (e/get-element-inner-html-el driver el)
               parse-transaction-row)))))


(defn- goal-save-transactions [driver el]
  (doto driver
    (e/click-el el)
    (e/wait-visible transaction-row))
  (let [transactions (doall (parse-transaction-table driver))]
    (doto driver
      (route-home)
      (route-goal-saves))
    transactions))


(defn- all-goal-save-transactions [driver]
  (let [get-goal-saves #(e/query-all driver {:tag :a :class "go-to-detail-link font-bold"})]
    (loop [x 0
           transactions []]
      (let [goal-saves (get-goal-saves)]
        (if (<= (- (count goal-saves) x) 0)
          transactions
          (let [el (nth goal-saves x)
                title (e/get-element-inner-html-el driver el)
                trans (->> (goal-save-transactions driver el)
                           (map #(assoc % :account title :type :goal-save :index x))
                           doall)]
            (recur (inc x)
                   (into transactions trans))))))))

(def ^:private indexes ["A" "B" "C" "D" "E" "F" "G" "H" "I" "J"])
(defn- ->index [prefix n] (str prefix (nth indexes n)))

(defn fetch-transactions [prefix username password every-day-ynab-account-id goal-save-ynab-account-id]
  (e/with-driver :chrome {} driver
    (login driver username password)
    (e/wait driver 10)
    (route-everyday-account driver)
    (let [everyday-transactions  (->> (parse-transaction-table driver)
                                      (map #(assoc % :type :every-day))
                                      doall)]
      (route-home driver)
      (route-goal-saves driver)
      (let [goal-save-transactions (all-goal-save-transactions driver)]
        (->> (concat everyday-transactions goal-save-transactions)
             (map (fn [{:keys [date amount description type index]}]
                    (-> (if (= :every-day type)
                          (ynab/->transaction prefix every-day-ynab-account-id date amount description)
                          (ynab/->transaction (->index prefix index) goal-save-ynab-account-id date amount description))
                        (assoc :account-name (name type))))))))))
