(ns rb.fnb
  (:require [etaoin.api :as e]
            [clojure.string :as string]))

(defn- driver [download-dir] (e/chrome {:download-dir download-dir}))

(defn- login [driver username password]
  (doto driver
    (e/go "https://www.fnb.co.za/")
    (e/fill {:tag :input :name :Username} username)
    (e/fill {:tag :input :name :Password} password)
    (e/click {:tag :input :id :OBSubmit})))


(defn- browse-to-accounts [driver]
  (let [q_accounts_button [{:class "iconButtonGroup clearfix big"} {:class "iconButton"}]]
    (doto driver
      (e/wait-visible q_accounts_button)
      (e/click q_accounts_button))))


(defn- account-links [driver]
  (e/wait-visible driver {:name :accountNumber})
  (let [account-numbers (->> (e/query-all driver {:name :accountNumber})
                             (map #(e/get-element-inner-html-el driver %))
                             (map string/trim))
        account-links (->> (e/query-all driver [{:name :nickname}])
                           (map #(e/child driver % {:tag :a})))]
    (zipmap account-numbers account-links)))


(defn- back-to-accounts [driver]
  (e/click driver {:tag :div :id :menuBtn})
  (e/wait driver 1)
  (let [accounts-btn (second (e/query-all driver {:tag :div :class :iconButton}))]
    (e/click-el driver accounts-btn))
  (e/wait driver 2))


(defn- download-transaction-history-for-account [driver account-number]
  (let [account-links (account-links driver)
        link-e (get account-links account-number)
        download-button-q {:tag :div :class "tableActionButton downloadButton"}
        dropdown-q {:tag :div :id :downloadFormat_dropId}
        option-q {:tag :li :class "dropdown-item" :data-value "ofx"}
        button-q {:tag :div :id :mainDownloadBtn}]
    (if link-e
      (doto driver
        (e/scroll-down (- (:y (e/get-element-location-el driver link-e)) 500))
        (e/wait 1)
        (e/click-el link-e)
        (e/wait-visible download-button-q)
        (e/wait 2)
        (e/click download-button-q)
        (e/wait-visible dropdown-q)
        (e/wait 2)
        (e/click dropdown-q)
        (e/wait-visible option-q)
        (e/wait 2)
        (e/click option-q)
        (e/wait 2)
        (e/click button-q)
        (e/wait 2)
        (back-to-accounts))
      (prn "No Link Found"))))


(defn download-ofx [config]
  (let [{:keys [fnb downloads-folder accounts]} config
        {:keys [username password]} fnb
        accounts (vals accounts)
        d (driver downloads-folder)]
    (login d username password)
    (browse-to-accounts d)
    (doseq [{:keys [bank-account-number name]} accounts]
      (prn (str "Downloading: " name))
      (download-transaction-history-for-account d bank-account-number))))
