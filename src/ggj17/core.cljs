(ns ggj17.core
  (:require [infinitelives.pixi.canvas :as c]
            [infinitelives.pixi.resources :as r]
            [infinitelives.pixi.texture :as t]
            [infinitelives.pixi.tilemap :as tm]            
            [infinitelives.pixi.sprite :as s]
            [infinitelives.utils.events :as e]
            [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.gamepad :as gp]
            [infinitelives.utils.pathfind :as path]
            [infinitelives.utils.console :refer [log]]
            
            [ggj17.assets :as assets]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   ))

(enable-console-print!)

(println "This text is printed from src/ggj17/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce state (atom {:text "Hello world!"}))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  (swap! state update-in [:__figwheel_counter] inc)
  )

(defonce bg-colour 0x52c0e5)

(defonce canvas
  (c/init {:layers [:bg :ocean :player :ui]
           :background bg-colour
           :expand true}))

(def scale 3)

(defonce main
  (go-until-reload
   state
                                        ; load resource url with tile sheet
   (<! (r/load-resources canvas :ui ["img/spritesheet.png"]))
    
   (t/load-sprite-sheet!
    (r/get-texture :spritesheet :nearest)
    assets/sprites)

   (m/with-sprite :player
     [player (s/make-sprite :boat
                            :scale scale
                            :x 0 :y 0)]
     (while true
       (<! (e/next-frame)))

     )
    
   ))

