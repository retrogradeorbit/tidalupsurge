
(ns ggj17.wave
  (:require [infinitelives.pixi.canvas :as c]
            [infinitelives.pixi.resources :as r]
            [infinitelives.pixi.texture :as t]
            [infinitelives.pixi.tilemap :as tm]
            [infinitelives.pixi.sprite :as s]
            [infinitelives.pixi.pixelfont :as pf]
            [infinitelives.utils.events :as e]
            [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.gamepad :as gp]
            [infinitelives.utils.pathfind :as path]
            [infinitelives.utils.console :refer [log]]
            [infinitelives.utils.sound :as sound]

            [ggj17.assets :as assets]
            [ggj17.state :as state]
            [ggj17.explosion :as explosion])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   [infinitelives.pixi.pixelfont :as pf]
                   ))

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

uniform float seaHue;
uniform float skyHue;

float rand(vec2 co){
  return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

vec3 hsv2rgb (vec3 c)
{
  vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main()
{
  float x = vTextureCoord.x * width - (width/2.0);
  float topWave =  abs(0.02 * (sin(freq * 20.0 * x)));
  float y = ((amp * sin(freq * (x + phase))) + (height/2.0)) / height;
  if (vTextureCoord.y < (y + topWave))
  {
    gl_FragColor = vec4(hsv2rgb(vec3(skyHue, (1.0 - vTextureCoord.y) * 0.5, 1.0)), 1.0);
  }
  else
  {
    // More green, less blue as we get to the bottom
    gl_FragColor = vec4(hsv2rgb(vec3(seaHue, 1.0, (1.0 - vTextureCoord.y))), 1.0);
  }
}
"
  )

(defn wave-y-position [width height amp freq phase x]

  (*
     amp
     (Math/sin
      (+
       0
       (* freq (+ phase x))))))

(defn wave-theta [width height amp freq phase x]
  (* 0.7
     (Math/atan
      (Math/cos
       (+ phase
          (* freq (+ phase x)))))))

(defn set-shader-uniforms [shader amp freq phase]
  (set! (.-uniforms.amp.value shader) amp)
  (set! (.-uniforms.freq.value shader) freq)
  (set! (.-uniforms.phase.value shader) phase)
  (set! (.-uniforms.width.value shader) (.-innerWidth js/window))
  (set! (.-uniforms.height.value shader) (.-innerHeight js/window))
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

        "skyHue" #js {"type" "1f" "value" 0.65}
        "seaHue" #js {"type" "1f" "value" 0.65}

        }))


(defn on-wave? [pos width height amp freq phase]
  (let [[x y] (vec2/as-vector pos)
        x (/ x 2)
        wave-y (wave-y-position width height amp freq phase x)]
    (>= y wave-y)))

(defn constrain-pos [pos width height amp freq phase]
  (let [[x y] (vec2/as-vector pos)
        x (/ x 2)
        wave-y (wave-y-position width height amp freq phase x)]
    (vec2/vec2 x (if (on-wave? pos width height amp freq phase)
                   wave-y
                   y))))

(defn update-background [shader fnum amp freq phase width height]
  (log "update-background:" amp freq phase)
  (set-shader-uniforms shader amp freq phase))


(defn update-colours [shader sky-hue sea-hue]
  (set! (.-uniforms.skyHue.value shader) sky-hue)
  (set! (.-uniforms.seaHue.value shader) sea-hue) )

(defn update-wave [shader fnum level-x amp freq phase sky-hue sea-hue]
  (update-background shader fnum amp freq (+ level-x phase)
                     (.-innerWidth js/window)
                     (.-innerHeight js/window))
  (update-colours shader sky-hue sea-hue)
  (swap! state/state
         #(-> %
              (assoc-in [:wave :fnum] fnum)
              (assoc-in [:colours :sky-hue] (+ (state/get-sky-hue) 0.0001))
              (assoc-in [:colours :sea-hue] (+ (state/get-sea-hue) 0.0001))
                                        ;(assoc-in [:level-x] (/ fnum 15))
              )))

(defn wave-update-thread [shader]
  (go
    (loop [fnum 0]
      (let [{:keys [level-x wave colours]} @state/state
            {:keys [amp freq phase]} wave
            {:keys [sky-hue sea-hue]} colours
            ]
        (update-wave shader fnum level-x amp freq phase sky-hue sea-hue)
        (<! (e/next-frame))
        (recur (inc fnum))))))
