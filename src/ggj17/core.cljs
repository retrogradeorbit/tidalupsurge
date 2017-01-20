(ns ggj17.core
  (:require [infinitelives.pixi.canvas :as c]
            [infinitelives.pixi.resources :as r]
            [infinitelives.pixi.texture :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]))

(enable-console-print!)

(println "This text is printed from src/ggj17/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(defonce bg-colour 0x5D0711)

(defonce canvas (c/init {:layers [:bg :tilemap :ui]
           :background bg-colour
           :expand true}))

(defonce main
  (go
    ; load resource url with tile sheet
    (<! (r/load-resources canvas :ui ["img/spritesheet.png"]))
    
    #_ (t/load-sprite-sheet!
     (r/get-texture :notlink :nearest)
     hero)
    
    ))

