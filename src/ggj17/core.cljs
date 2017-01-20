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

	#define PROCESSING_COLOR_SHADER

	uniform float time;
	uniform vec2 resolution;
	uniform vec2 colorMult;
	uniform float coeffx;
	uniform float coeffy;
	uniform float coeffz;


	void main( void ) {

		vec2 position = gl_FragCoord.xy / resolution.xy;

		float color = 0.0;
		color += sin( position.x * cos( time / 15.0 ) * 10.0 )  +  cos( position.y * cos( time / 15.0 ) * coeffx );
		color += sin( position.y * sin( time / 10.0 ) * coeffz )  +  cos( position.x * sin( time / 25.0 ) * coeffy );
		color += sin( position.x * sin( time / 50.0 ) * coeffx )  +  sin( position.y * sin( time / 35.0 ) * coeffz );

		color *= sin( time / 10.0 ) * 0.5;

		float r = color;
		float g = color * colorMult.y;
		float b = sin( color + time / 2.0 ) * colorMult.x;

		gl_FragColor = vec4(r, g, b, 1.0 );

	}

  "
)


(defn wave-line [resolution]
  (js/PIXI.AbstractFilter.
   nil
   #js [fragment-shader-glsl]
   #js {"resolution" #js {"type" "2f" "value" (js/Float32Array. resolution)}}))




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


(defonce main
  (go
    ;-until-reload
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
     (set! (.-filters bg) (make-array (wave-line [1 1])))
     (while true
       (<! (e/next-frame)))

     )

   ))


