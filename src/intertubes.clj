(ns intertubes
  (:use compojure)
  (:require [clojure.contrib.sql :as sql])
  (:gen-class
   :extends javax.servlet.http.HttpServlet))

(declare generate-link-name)

(def base-url "http://the.intertub.es")

(when false
  (def db {:classname   "org.postgresql.Driver"
           :subprotocol "postgresql"
           :subname     "intertubes"}))

(def db {:name "java:/comp/env/jdbc/intertubes"})

(defn basic-page [title & body]
  (html
   [:html
    [:head [:title title]]
    [:body
     [:h1 "The Intertubes"]
     body]]))

(defn create-page []
  (basic-page "Create"
             [:form {:method "post" :action "/create"}
              [:input {:name "url"}]]))

(defn success-page [slug url]
  (let [slug-url (str base-url "/" slug)]
    (basic-page "Created"
      [:h3 (link-to slug-url slug-url)]
      [:p "now redirects to"]
      [:h3 url])))

(defn taken? [slug]
  (sql/with-query-results res
    ["select count(slug) from links where slug = ?", slug]
    (= res 1)))

(defn lookup-slug [slug]
  (sql/with-query-results res
    ["select slug,url from links where slug = ?", slug]
    (first res)))

(defn lookup-url [url]
  (sql/with-query-results res
    ["select slug,url from links where url = ?", url]
    (first res)))

(defn prefix? [xs ys]
  (cond (and (not-empty xs) (not-empty ys)
             (= (first xs) (first ys)))
        (prefix? (rest xs) (rest ys))

        (empty? xs)
        true

        (empty? ys)
        false))

(defn url-filter [url]
  (str (cond (not (or (prefix? "http://" url) (prefix? "https://" url)))
             "http://"

             true nil)
       url))


(defn add-url [request]
  (let [url (url-filter (-> request :params :url))
        ip (:remote-addr request)]
    (cond (not url)
          (basic-page "Invalid" [:p "Cannot link to that URL"])

          true
          (sql/with-connection db
            (let [existing (lookup-url url)]
              (cond existing
                    (success-page (existing :slug) (existing :url))

                    true
                    (let [link-name (first (drop-while #(taken? %)
                                                       (take 5 (repeatedly generate-link-name))))]
                      (sql/insert-values "links" ["slug", "url", "creator"] [link-name url ip])
                      (success-page link-name url))))))))

(defn record-redirect [request slug]
  (let [ip (:remote-addr request)
        referer (get (:headers request) "referer")]
    (sql/insert-values "hits" ["ip","referer","slug"] [ip referer slug])))


(defn redirect [request]
  (let [slug (-> request :route-params :slug)]
    (sql/with-connection db
      (let [redir (lookup-slug slug)]
        (cond redir (do
                      (record-redirect request slug)
                      (redirect-to (redir :url)))
              true (basic-page "Error" [:p "No such url"]))))))

(def slug-parts "1234567890asdfghijklmnopqrstuvwxyz")
(def slug-size 6)

(defn generate-link-name []
  (apply str (take slug-size
                   (let [l (count slug-parts)]
                     (repeatedly #(nth slug-parts (rand-int l)))))))

(defroutes intertubes-app
  (GET "/create" (create-page))
  (POST "/create" add-url)
  (GET "/:slug" redirect)
  (GET "/" (create-page)))

(defservice intertubes-app)

(defn start-devel-server []
  (run-server {:port 8081}
              "/*" (servlet intertubes-app)))
