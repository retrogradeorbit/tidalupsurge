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
            [ggj17.state :as state]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   ))


(enable-console-print!)

(def gravity (vec2/vec2 0 0.1))

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
  (swap! state/state update-in [:__figwheel_counter] inc)
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

(defn start-pressed? []
  (e/is-pressed? :space))

(defn titlescreen [frame]
  (go-while (start-pressed?)
    (m/with-sprite canvas :player
      [title-text (s/make-sprite  :title-text :scale scale :x 0 :y 0)]
        (s/set-y! title-text frame))))

(defn on-wave? [pos width height amp freq phase]
  (let [[x y] (vec2/as-vector pos)
        wave-y (wave-y-position width height amp freq phase x)]
    (>= y wave-y)))

(defn constrain-pos [pos width height amp freq phase]
  (let [[x y] (vec2/as-vector pos)
        wave-y (wave-y-position width height amp freq phase x)]
    (vec2/vec2 x (if (on-wave? pos width height amp freq phase) wave-y y))))


(defn update-background [shader clouds-front clouds-back fnum amp freq phase width height]
  (set-shader-uniforms shader fnum amp freq phase)

  (shift-clouds clouds-front fnum width (+ (/ height -2) 150) 1)
  (shift-clouds clouds-back fnum width (+ (/ height -2) 50) 0.8)
  )

(defn wave-update-thread [shader clouds-front clouds-back]
  (go
    (loop [fnum 0]
      (let [{:keys [amp freq phase]} (:wave @state/state)]
        (update-background shader clouds-front clouds-back fnum amp freq phase
                           (.-innerWidth js/window)
                           (.-innerHeight js/window))
        (swap! state/state
               #(-> %
                    (assoc-in [:wave :fnum] fnum)
                    (assoc-in [:wave :phase] (/ fnum 15)))
               )
        (<! (e/next-frame))
        (recur (inc fnum))))))

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

        (wave-update-thread shader clouds-front clouds-back)
    
        (loop [fnum 0
               pos (vec2/vec2 0 0)
               vel (vec2/vec2 0 0)
               heading 0
               heading-delta 0
               ]
          (let [
                {:keys [amp freq phase]} (:wave @state/state)

                height (.-innerHeight js/window)
                width (.-innerWidth js/window)

                joy (get-player-input-vec2)
                joy-x (vec2/get-x joy)
                joy-y (vec2/get-y joy)
                half-width (/ (.-innerHeight js/window) 2)

                vel (vec2/add vel gravity)
                pos2 (vec2/add pos vel)

                player-on-wave? (on-wave? pos2 width height amp freq phase)

                constrained-pos (constrain-pos pos2 width height amp freq phase)

                ;; now calculate the vel we pass through to next iter from our changed position
                vel (vec2/sub constrained-pos pos)

                heading (if player-on-wave?
                          (Math/atan (* 0.2 (Math/cos (+ (/ (* 640 freq 0.25) width) phase))))
                          (+ heading heading-delta))

                heading-delta (if player-on-wave? 0 (+ heading-delta (* joy-y 0.01)))

                ;; damped heading delta back to 0
                heading-delta (if (neg? heading-delta)
                                (min 0 (+ heading-delta 0.001))
                                (max 0 (- heading-delta 0.001)))
                ]            

            ;(titlescreen fnum)

			(s/set-pos! player constrained-pos)
            (s/set-rotation! player heading)


            (<! (e/next-frame))
            (recur (inc fnum)
                   (vec2/add constrained-pos joy)
                   vel
                   heading
                   heading-delta))
          )))

      )

    ))


