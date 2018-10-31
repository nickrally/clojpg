(ns clojpg.core-test
  (:require [clojure.test :refer :all]
            [clojpg.handler :refer :all]
            [ring.mock.request :as mock]
            [cheshire.core :as cheshire]))

(testing "GET request to /event returns 200"
  (let [response (app (-> (mock/request :get  "/event")))]
    (is (= (:status response) 200))))

(testing "not-found"
  (let [response (app (-> (mock/request :get  "/foo")))]
    (is (= (:status response) 404))))