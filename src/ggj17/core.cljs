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

(def fragment-shader-glsl

  "

precision mediump float;
varying vec2 vTextureCoord;
varying vec4 vColor;

uniform float amp;
uniform float freq;
uniform float phase;

void main()
{
  if (vTextureCoord.y < (0.5 +  amp * sin(freq * vTextureCoord.x + phase)))
  {
    gl_FragColor = vec4(0.0, 1.0, 1.0, 1.0);
  }
  else
  {
    gl_FragColor = vec4(0.0, 0.0, 0.6, 1.0);
  }
}
"
)

(defn wave-line [resolution]
  (js/PIXI.AbstractFilter.
   nil
   #js [fragment-shader-glsl]
   #js {
        "amp" #js {"type" "1f" "value" 10.0}
        "freq" #js {"type" "1f" "value" 10.0}
        "phase" #js {"type" "1f" "value" 0.0}

        }))


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

(defn make-background []
  (let [bg (js/PIXI.Graphics.)
        border-colour 0x000000
        width 32
        height 32
        full-colour 0xff0000 
        ]
    (doto bg
      (.beginFill 0xff0000)
      (.lineStyle 0 border-colour)
      (.drawRect 0 0 width height)
      (.lineStyle 0 border-colour)
      (.beginFill full-colour)
      (.drawRect 0 0 32 32)
      .endFill)
    (.generateTexture bg false)))


(defn set-shader-uniforms [shader fnum]
  (set! (.-uniforms.amp.value shader) (* 0.1 (Math/sin (/ fnum 20))))
  (set! (.-uniforms.freq.value shader) 10.0)
  (set! (.-uniforms.phase.value shader) (* fnum 0.03))
  )

(defn set-texture-filter [texture filter]
  (set! (.-filters texture) (make-array filter ))
  )

(def main
  (go ;-until-reload
   ;state
                                        ; load resource url with tile sheet
   (<! (r/load-resources canvas :ui ["img/spritesheet.png"]))

   (t/load-sprite-sheet!
    (r/get-texture :spritesheet :nearest)
    assets/sprites)

   (m/with-sprite :player
     [
      bg (s/make-sprite (make-background) :scale 100)
      player (s/make-sprite :boat
                            :scale scale
                            :x 0 :y 0)]

     (let [shader (wave-line [1 1])]
       (set-texture-filter bg shader)
       (loop [fnum 0]
         (set-shader-uniforms shader fnum)
         (<! (e/next-frame))
         (recur (inc fnum))
         ))

     )

   ))


