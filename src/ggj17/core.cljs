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

uniform float width;
uniform float height;

void main()
{
  if (vTextureCoord.y < (0.5 + (amp / height) * sin(((640.0 * freq / width) * vTextureCoord.x + phase))))
  {
    gl_FragColor = vec4(0.0, 1.0, 1.0, 1.0);
  }
  else
  {
    // More green, less blue as we get to the bottom
    gl_FragColor = vec4(0.0, vTextureCoord.y * 0.5, vTextureCoord.y, 1.0);
  }
}
"
)

(defn wave-line [resolution]
  (js/PIXI.AbstractFilter.
   nil
   #js [fragment-shader-glsl]
   #js {
        "amp" #js {"type" "1f" "value" 100.0}
        "freq" #js {"type" "1f" "value" 100.0}
        "phase" #js {"type" "1f" "value" 0.0}
        "width" #js {"type" "1f" "value" 300}
        "height" #js {"type" "1f" "value" 300}

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
  (set! (.-uniforms.amp.value shader) (* 30 (Math/sin (/ fnum 20))))
  (set! (.-uniforms.freq.value shader) 10.0)
  (set! (.-uniforms.phase.value shader) (* fnum 0.03))
  (set! (.-uniforms.width.value shader) (.-innerWidth js/window))
  (set! (.-uniforms.height.value shader) (.-innerHeight js/window))
  )

(defn set-texture-filter [texture filter]
  (set! (.-filters texture) (make-array filter)))

(defonce main
  (go                              ;-until-reload
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

      (let [shader (wave-line [1 1])
            ]
        (set-texture-filter bg shader)
        (loop [fnum 0]
          (let [amp (* 30 (Math/sin (/ fnum 20)))
                height (.-innerHeight js/window)
                width (.-innerWidth js/window)
                phase (* fnum 0.03)
                freq 10
                ]
            (set-shader-uniforms shader fnum)

            (s/set-pos! player 0
                        (- (* height (*
                                      (/ amp height)
                                      (Math/sin (+ (/ (* 640 freq 0.5) width) phase)))) 20)

                        )

            (s/set-rotation!
             player
             (Math/atan
              (*
                  0.2
                  (Math/cos (+ (/ (* 640 freq 0.25) width) phase)))
              )
             )
            
           
            (<! (e/next-frame))
            (recur (inc fnum)))
          ))

      )

    ))


