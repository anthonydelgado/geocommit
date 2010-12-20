					; geocommit signup
					; (c) 2010 The Geocommit Project
					; (c) 2010 David Soria Parra
					; Licensed under the terms of the MIT License
(ns geocommit.signup
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use geocommit.core
;	geocommit.mail
	compojure.core
	experimentalworks.couchdb
	clojure.contrib.logging
	clojure.contrib.json
	[ring.util.servlet :only [defservice]])
  (:import (org.apache.commons.validator EmailValidator)
	   (java.util UUID))
  (:require [compojure.route :as route]
	    [clojure.contrib.trace :as t]))

(def *couchdb*
     "http://geocommit:geocommit@dsp.couchone.com/invites")

(defstruct invite :_id :date :mail :invitecode :active :verifycode :verified)

(defn validate-email [mail]
  (.isValid (EmailValidator/getInstance) mail))

(defn- create-verify-code []
  (.toString (UUID/randomUUID)))

(defn verify-code [code]
  (if-let [res (couch-view *couchdb* "views" "verifycode" [code] {:include_docs true})]
    (if (= (count (res :rows)) 1)
      (first (res :rows)))
    nil))

(defroutes signup-routes
  (GET "/signup/verify/:code" [code]
       (if-let [res (verify-code code)]
	 (do
	   (couch-update *couchdb* (res :id) (assoc (res :doc) :verified true))
	   {:status 200 :body "Thank you. Verification successful"})
	 {:status 200 :body "Cannot verify."}))
  (POST "/signup/" [mailaddr]
	(let [code (create-verify-code)]
	  (if (and
	       (validate-email mailaddr)
	       (couch-add *couchdb*
			  (struct invite
				  (str "mail:" mailaddr)
				  (isodate)
				  mailaddr
				  nil
				  nil
				  code
				  false)))
	    (do
;	      (send-mail "no-reply@geocommit.com"
;			 mailaddr
;			 "Welcome to geocommit.com. Please verify your invitation request."
;			 (str "Follow the link to verify your invitation request\n\n"
;			      "http://geocommit.com/signup/verify/" code))
	      {:status 201})
	    {:status 400})))

  (route/not-found "not a valid request"))

(defservice signup-routes)