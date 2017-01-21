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

vec3 hsv2rgb (vec3 c)
    {
      vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
      vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
      return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

void main()
{
  float x = vTextureCoord.x * width - (width/2.0);
  float y = ((amp * sin(freq * x + phase)) + (height/2.0)) / height;
  if (vTextureCoord.y < y)
  {
    gl_FragColor = vec4(hsv2rgb(vec3(0.65, (1.0 - vTextureCoord.y) * 0.5, 1.0)), 1.0);
  }
  else
  {
    // More green, less blue as we get to the bottom
    gl_FragColor = vec4(0.0, vTextureCoord.y * 0.1, 1.0 - vTextureCoord.y, 1.0);
  }
}
"
  )

(defn wave-y-position [width height amp freq phase x]

  (*
     amp
     (Math/sin
         (+ phase
            (* freq x))
         )
     )
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


(defn set-shader-uniforms [shader fnum amp freq phase]
  (set! (.-uniforms.amp.value shader) amp)
  (set! (.-uniforms.freq.value shader) freq)
  (set! (.-uniforms.phase.value shader) phase)
  (set! (.-uniforms.width.value shader) (.-innerWidth js/window))
  (set! (.-uniforms.height.value shader) (.-innerHeight js/window))
  )

(defn set-texture-filter [texture filter]
  (set! (.-filters texture) (make-array filter)))

(defn get-player-input-vec2 []
  (vec2/vec2 (or (gp/axis 0)
                 (cond (e/is-pressed? :left) -1
                       (e/is-pressed? :right) 1
                       :default 0) )
             (or (gp/axis 1)
                 (cond (e/is-pressed? :up) -1
                       (e/is-pressed? :down) 1
                       :default 0))))

(defn make-clouds [width scale-factor]
  (vec
    (let [num-clouds (/ width 200)]
      (for [cloud-num (range num-clouds)]
        (s/make-sprite :cloud
                       :scale (* scale scale-factor)
                       :x 0 :y 200)))))

(defn shift-clouds [clouds frame width y-pos time-scale]
  (vec (map-indexed
    (fn [idx cloud]
      (let [shift (* 250 idx)
            width (+ width (* 2 scale 48))
            x-pos (- (mod (+ shift (* frame time-scale)) width) (/ width 2))]
        (s/set-pos! cloud x-pos y-pos )))
    clouds)))


(defn start? []
  (e/is-pressed? :space))

(defn titlescreen []
  (go-while (not (start?))
    (m/with-sprite canvas :player
      [title-text (s/make-sprite  :title-text :scale scale :x 0 :y 0)]
      (loop [frame 0]
        (s/set-y! title-text frame)
        (<! (e/next-frame))
        (recur (inc frame))))))


(defn float-boat [player xpos height width amp freq phase]
  (s/set-pos! player xpos
              (wave-y-position
               width height
               amp freq phase
               xpos)
              #_
              (- (* height (*
                            (/ amp height)
                            (Math/sin (+ (/ (* freq (+ xpos half-width)) width) phase)))) 20))

  )

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
       (m/with-sprite-set :player
        [clouds-front (make-clouds (.-innerWidth js/window) 1.0)
         clouds-back (make-clouds (.-innerWidth js/window) 0.8)]

      (let [shader (wave-line [1 1])
            ]
        (set-texture-filter bg shader)

        (<! (titlescreen))

        (loop [fnum 0
               xpos 0]
          (let [amp 100 ;(* 50 (Math/sin (/ fnum 20)))
                phase (/ fnum 5)
                freq 0.01
                height (.-innerHeight js/window)
                width (.-innerWidth js/window)

                joy (get-player-input-vec2)
                half-width (/ (.-innerHeight js/window) 2)]
            (set-shader-uniforms shader fnum amp freq phase)

            (float-boat player xpos height width amp freq phase)

            (shift-clouds clouds-front fnum width (+ (/ height -2) 150) 1)
            (shift-clouds clouds-back fnum width (+ (/ height -2) 50) 0.8)

            (s/set-rotation!
             player
             (Math/atan
              (*
                  0.2
                  (Math/cos (+ (/ (* 640 freq 0.25) width) phase)))
              )
             )


            (<! (e/next-frame))
            (recur (inc fnum) (+ xpos (* 3 (vec2/get-x joy)))))
          )))

      )

    ))


