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

(defonce canvas
  (c/init
   {:expand true
    :engine :auto
    :layers [:bg :world :float :ui]
    :background 0x505050
    }))



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

	(set! (.-filters main) (make-array (wave-line [1 1])))

    ))
