					; geocommit.com HTTP hook API
					; (c) 2010 The Geocommit Project
					; (c) 2010 David Soria Parra
					; Licensed under the terms of the MIT License
(ns #^{:doc "HTTP signup API functions",
       :author "David Soria Parra"}
  geocommit.hook
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use geocommit.core
	geocommit.config
	experimentalworks.couchdb
	clojure.contrib.logging
	clojure.contrib.json
	clojure.contrib.condition
	[ring.util.servlet :only [defservice]])
  (:require [compojure.route :as route]
	    [appengine-magic.core :as ae]
	    [clojure.contrib.trace :as t]
	    [clojure.contrib.http.agent :as agent])
  (:import java.net.URI))

(def *couchdb* (get-config :databases :geocommits))
(defstruct repository :_id :identifier :name :description :repository-url :vcs :scanned :type)

(defn- contains-all?
  "Like clojure.core/contains? but allows multiple keys.
   (contains-all? map :foo :bar) is equal to (and (contains? map :foo) (contains? map :bar))"
  [val & keys]
  (every? #(= true %)
	  (map #(clojure.core/contains? val %) keys)))

(defn is-tracked?
  "Check if the given repository identifier is already tracked"
  [ident]
  (map? (couch-get *couchdb* (str "repository:" ident))))

(defn string-json
  "Like clojure.contrib.http.agent/string but tries to parse the result as string.

   Conditions:
     :parse-error If the result cannot be parsed correctly"
  [agent]
  (let [str (agent/string agent)]
    (try
      (read-json str)
      (catch Exception e
	(raise :type :parse-error)))))

(defn- send-scan
  "Send a scan to the fetch service"
  [ident repository]
  (handler-case :type
    (:job (string-json (agent/http-agent (t/trace (get-config :api :initscan))
					 :body (json-str {:identifier ident
							  :repository-url repository})
					 :method "POST")))
    (handle :parse-error
      (raise :type :service-error))))

(defn- scan
  "Add a scan job to the database"
  [ident name desc repourl vcs]
  (and (couch-add *couchdb*
		  (struct repository
			  (str "repository:" ident)
			  ident name desc repourl vcs false "repository"))
       (send-scan ident repourl)))
  
(defn- guess-origin
  "Heuristic to determine the origin of the hook request.
   Returns :github or :bitbucket"
  [payload]
  (cond
   (contains-all? payload :broker :service) :bitbucket
   (contains-all? payload :before :ref) :github))

(defn- bitbucket-update-parser
  "Parse bitbucket.org commits"
  [ident commits]
  (let [ctx (remove nil? (map #(parse-geocommit ident (%1 :node) (%1 :author) (%1 :message) (%1 :message)) commits))]
    (if (empty? ctx)
      nil ctx)))

(defn- bitbucket [ident payload]
  (if (is-tracked? ident)
    (if-let [ctx (bitbucket-update-parser ident (payload :commits))]
      (if-let [res (couch-bulk-update *couchdb* ctx)]
	{:status 201}
	{:status 400})
      {:status 200})
    (if (scan ident
	      (-> payload :repository :name)
	      (-> payload :repository :slug)
	      (-> payload :repository :absolute_url)
	      "mercurial")
      {:status 200})))

(defn- ident-to-repository [ident]
  (str "git://" ident " .git"))

(defn- ident-from-url
  "Takes a URL like http://github.com/foo and returns a repository identifier.

   Example:
     http://github.com/dsp/geocommit -> github.com/dsp/gecommit"
  [url]
  (if url
    (.replaceFirst url "(http://|https://)" "")
    (raise :type :parse-error)))

(defn- github-api-url
  "Takes an identifier and API call location like /blob/show/ and an infinite
   number of additional parameters and builds a normalized github.com API URL.

   It is necesary to normalize a github.com API URL."
  [ident sub & rest]
  (str (.normalize (URI. (str
			  (get-config :api :github)
			  sub
			  (.replaceFirst ident "github\\.com/" "")
			  "/"
			  (apply str rest))))))

(defn- github-notes-commit
  "Return refs/notes/geocommit for a repository by using the fetchservice"
  [repository-url]
  (handler-case :type
    (:refs/notes/geocommit (string-json (agent/http-agent (get-config :api :fetchservice)
							  :method "POST"
							  :body (json-str {:repository-url repository-url}))))
    (handle :parse-error
      (raise :type :service-error))))

(defn- github-fetch-note
  "Return the the geocommit note for a git SHA1.
   We need the correct note-commit tree id."
  [ident note-commit id]
  (-> (string-json (agent/http-agent (str (github-api-url ident "/blob/show/" note-commit "/" id))))
      :blob :data))

(defn- parse-github-geocommit
  "Parse a github geocommit note. and return a geocommit struct."
  [ident note-commit commit]
  (let [{id :id {name :name mail :email} :author message :message}
	commit]
    (parse-geocommit ident id (str name " <" mail ">") message
		     (github-fetch-note ident note-commit id))))

(defn github-update-parser
  "Parse a sequence of github commits."
  [ident commits]
  (if-let [notehash (github-notes-commit (ident-to-repository ident))]
    (if-let [ctx (remove nil? (map #(parse-github-geocommit ident notehash %) commits))]
      (if (empty? ctx)
	nil ctx))))
  
(defn- github [ident payload]
  (if (is-tracked? ident)
    (if-let [ctx (github-update-parser ident (:commits payload))]
      (if-let [res (couch-bulk-update *couchdb* ctx)]
	{:status 201}
	{:status 400})
      {:status 200})
    (if (scan ident
	      (-> payload :repository :name)
	      (-> payload :repository :description)
	      (str "git://" ident ".git")
	      "git")
    {:status 200})))

(defn app-hook
  "API entry point for the github/bitbucket geocommit receive service."
  [rawpayload]
  (if rawpayload
    (let [payload (read-json rawpayload)]
      (handler-case :type
	(condp = (guess-origin payload)
	    :github (github (ident-from-url (-> payload :repository :url)) payload)
	    :bitbucket (bitbucket (str "bitbucket.org"
				       (-> payload :repository :absolute_url))
				  payload))
	(handle :parse-error
	  (error (:message "parse error"))
	  {:status 400})
	(handle :service-error
	  {:status 500})))
    {:status 400}))