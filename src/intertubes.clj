(ns intertubes
  (:use compojure)
  (:require [clojure.contrib
             [sql :as sql]
             [seq-utils :as seq]])
  (:gen-class
   :extends javax.servlet.http.HttpServlet))

(declare generate-link-name)

(def base-url "http://the.intertub.es")

(when false
  (def db {:classname   "org.postgresql.Driver"
           :subprotocol "postgresql"
           :subname     "intertubes"}))

(def db {:name "java:/comp/env/jdbc/intertubes"})

(defn preview-enabled [request]
  (= (-> request :cookies :preview) "true"))

(defn basic-page [request title & body]
  (let [preview-links (cond (preview-enabled request)
                            (link-to "/disable-preview" "Disable Preview")

                            :else
                            (link-to "/enable-preview" "Enable Preview"))]
    (html
     [:html
      [:head
       [:title title " - the.intertub.es"]
       (include-css "/style.css")]
      [:body
       [:div {:class "divitis"}
        [:h1 "The Intertubes"]
        body
        [:div {:class "footer"} preview-links]]]])))

(defn link-slug [slug]
  (let [x (str base-url "/" slug)]
    (link-to x x)))

(defn create-page [request]
  (basic-page request "create"
              [:form {:method "post" :action "/create"}
               [:input {:name "url"}]]))

(defn success-page
  ([request title slug url]
     (success-page request title slug url "now redirects to" "success"))
  ([request title slug url text class]
     (basic-page request title
       [:div {:class class}
        [:h3 (link-slug slug)]
        [:p text]
        [:h3 (link-to url url)]])))

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
        (recur (rest xs) (rest ys))

        (empty? xs)
        true

        (empty? ys)
        false))

(defn url-filter [url]
  (cond (not (seq/includes? url \.))
        nil

        :else
        (str (cond (not (or (prefix? "http://" url) (prefix? "https://" url)))
                   "http://"

                   :else nil)
             url)))


(defn add-url [request]
  (let [url (url-filter (-> request :params :url))
        ip  (:remote-addr request)]
    (cond (not url)
          (basic-page request "Invalid" [:p {:class "error"} "Cannot link to that URL"])

          true
          (sql/with-connection db
            (let [existing (lookup-url url)]
              (cond existing
                    (success-page request "created" (existing :slug) (existing :url))

                    :else
                    (let [link-name (first (drop-while #(taken? %)
                                                       (take 5 (repeatedly generate-link-name))))]
                      (sql/insert-values "links" ["slug" "url" "creator"] [link-name url ip])
                      (success-page request "created" link-name url))))))))

(defn record-redirect [request slug]
  (let [ip      (:remote-addr request)
        referer (get (:headers request) "referer")]
    (sql/insert-values "hits" ["ip" "referer" "slug"] [ip referer slug])))

(defn with-resolved-url [request f]
  (let [slug (-> request :route-params :slug)]
    (sql/with-connection db
      (let [redir (lookup-slug slug)
            url   (:url redir)]
        (cond (not redir) (basic-page request "error" [:p {:class "error"} "No such short url " \" slug \" ])
              :else       (f slug url))))))


(defn redirect [request]
  (with-resolved-url request
    (fn [slug url]
      (record-redirect request slug)
      (cond (preview-enabled request)
            (success-page request "preview" slug url "redirects to" "preview")

            :else
            (redirect-to url)))))

(defn set-preview [to]
  {:status 302 :headers {"Location" "/"
                         "Set-Cookie" (str "preview=" to "; path=/")}})

(defn preview [request]
  (with-resolved-url request
    (fn [slug url]
      (success-page request "preview" slug url "redirects to" "preview"))))

(def slug-parts "1234567890asdfghijklmnopqrstuvwxyz")
(def slug-size 6)

(defn generate-link-name []
  (apply str (take slug-size
                   (let [l (count slug-parts)]
                     (repeatedly #(nth slug-parts (rand-int l)))))))

(defn serve-resource [filename]
  (fn [request]
    (or (.getResourceAsStream (:servlet-context request) (str "/public/" filename))
        "fail")))

(defroutes intertubes-app
  (GET "/style.css"                (serve-resource "style.css"))
  (GET "/create"                   create-page)
  (GET "/enable-preview"           (set-preview "true"))
  (GET "/disable-preview"          (set-preview "false"))
  (POST "/create"                  add-url)
  (GET "/preview/:slug"            preview)
  (GET "/:slug"                    redirect)
  (GET "/"                         create-page)
  (ANY "*"                         (fn [r]
                                     {:status 404
                                      :body (basic-page r "error: 404" [:p {:class "error"} "Page not found"])})))

(defservice intertubes-app)

(defn start-devel-server []
  (run-server {:port 8081}
              "/*" (servlet intertubes-app)))
