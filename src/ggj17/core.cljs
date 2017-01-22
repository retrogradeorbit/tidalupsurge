(ns ggj17.core
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
            [ggj17.explosion :as explosion]
            [ggj17.state :as state]
            [ggj17.level :as level]
            [ggj17.clouds :as clouds]
            [ggj17.popup :as popup]
            [ggj17.floaty :as floaty]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   [infinitelives.pixi.pixelfont :as pf]
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

(def flip-text
  {
   -3 ["sick triple backflip!"]
   -2 ["double backflip"]
   -1 ["backward flip!"
       "reverse roll"
       "do a barrel roll"
       "backflip"]
   0 ["jump landed"
      "splash down"]
   1 ["sick flip bro!"
      "a full flip"
      "forward roll"]
   2 ["double flip!"
      "720!"
      "double forward roll"]
   3 ["triple flippage"
      "three full loops"
      "you're a loop master"]
   })

(def flip-score
  {
   -3 500
   -2 200
   -1 50
   0 5
   1 50
   2 200
   3 500
   })

(def flip-sfx
  {
   -3 :land1
   -2 :land1
   -1 :land1
   1 :land1
   2 :land1
   3 :land1
   })

(defn wave-y-position [width height amp freq phase x]

  (*
     amp
     (Math/sin
         (+ phase
            (* freq x)))))

(defn wave-theta [width height amp freq phase x]
  (* 0.7
     (Math/atan
      (Math/cos
       (+ phase
          (* freq x))))))

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
  (c/init {:layers [:bg :ocean :player :clouds :damage :score :ui]
           :background bg-colour
           :expand true
           :origins {:damage :bottom-right
                     :score :bottom-left}}))

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

(defn start-pressed? []
  (or
   (e/is-pressed? :space)
   (gp/button-pressed? 0 :a)
   (gp/button-pressed? 0 :b)
   (gp/button-pressed? 0 :x)
   (gp/button-pressed? 0 :y)))


(defn on-wave? [pos width height amp freq phase]
  (let [[x y] (vec2/as-vector pos)
        wave-y (wave-y-position width height amp freq phase x)]
    (>= y wave-y)))

(defn constrain-pos [pos width height amp freq phase]
  (let [[x y] (vec2/as-vector pos)
        wave-y (wave-y-position width height amp freq phase x)]
    (vec2/vec2 x (if (on-wave? pos width height amp freq phase) wave-y y))))

(defn update-background [shader fnum amp freq phase width height]
  (set-shader-uniforms shader fnum amp freq phase))


(defn wave-update-thread [shader]
  (go
    (loop [fnum 0]
      (let [{:keys [level-x wave]} @state/state
            {:keys [amp freq phase]} wave]
        (update-background shader fnum amp freq (+ level-x phase)
                           (.-innerWidth js/window)
                           (.-innerHeight js/window))
        (swap! state/state
               #(-> %
                    (assoc-in [:wave :fnum] fnum)
                    (assoc-in [:level-x] (/ fnum 15)))
               )
        (<! (e/next-frame))
        (recur (inc fnum))))))

(defn health-display-thread []
  (go-while (state/playing?)
    (m/with-sprite :damage
      [health-text (pf/make-text :small (->> @state/state :health int (str "hull "))
                                 :scale 3
                                 :x -110 :y -20)]
      (loop [health (:health @state/state)]
        (<! (e/next-frame))
        (let [new-health (:health @state/state)]
          (when (not= new-health health)
            (.removeChildren health-text)
            (pf/change-text! health-text :small (str "hull " (int new-health))))
          (recur new-health)))))
  )

(defn score-display-thread []
  (go-while (state/playing?)
    (m/with-sprite :score
      [score-text (pf/make-text :small (->> @state/state :score int str)
                                 :scale 5
                                 :x 30 :y -30)]
      (loop [score (:score @state/state)]
        (<! (e/next-frame))
        (let [new-score (:score @state/state)]
          (when (not= new-score score)
            (.removeChildren score-text)
            (pf/change-text! score-text :small (str (int new-score))))
          (recur new-score)))))
  )

(defn dead? []
  (or (e/is-pressed? :esc)
      false))

(defn jump-pressed? []
  (or
   (e/is-pressed? :space)
   (gp/button-pressed? 0 :a)
   (gp/button-pressed? 0 :b)
   (gp/button-pressed? 0 :x)
   (gp/button-pressed? 0 :y)))

(def jump-vec (vec2/vec2 0 -5))

(defn player-thread [player]
  (go-while
   (not (dead?))
   (state/set-amp! 1)
   (state/start-game!)

   (health-display-thread)
   (score-display-thread)
   (level/level-thread)

   (sound/play-sound :game-start 0.5 false)

   (loop [fnum 0
          pos (vec2/vec2 0 0)
          vel (vec2/vec2 0 0)
          heading 0
          heading-delta 0
          last-frame-on-wave? false
          total-delta 0
          ]
     (let [
           {:keys [wave level-x]} @state/state
           {:keys [amp freq phase]} wave
           wave-x-pos (+ level-x phase)

           height (.-innerHeight js/window)
           width (.-innerWidth js/window)

           joy (get-player-input-vec2)
           joy-x (vec2/get-x joy)
           joy-y (vec2/get-y joy)
           half-width (/ (.-innerHeight js/window) 2)

           vel (vec2/add vel gravity)
           pos2 (vec2/add pos vel)

           player-on-wave? (on-wave? pos2 width height amp freq wave-x-pos)

           pos2 (vec2/add pos2 (if (and player-on-wave? (jump-pressed?)) jump-vec (vec2/zero)))

           constrained-pos (constrain-pos pos2 width height amp freq wave-x-pos)

           ;; now calculate the vel we pass through to next iter from our changed position
           vel (vec2/sub constrained-pos pos)

           old-heading heading
           heading (if player-on-wave?
                     (wave-theta width height amp freq wave-x-pos (vec2/get-x pos2))
                     (+ heading heading-delta))

           heading-delta (if player-on-wave?
                           0
                           (+ heading-delta (* joy-y 0.01)))
           ;; damped heading delta back to 0
           heading-delta (if (neg? heading-delta)
                           (min 0 (+ heading-delta 0.001))
                           (max 0 (- heading-delta 0.001)))
           ]

       ;; landing
       (when (and (not last-frame-on-wave?) player-on-wave?)
         (let [flips (int (/ total-delta (* 2 Math/PI)))
               heading-diff (Math/abs (- heading old-heading))]
           (when (> heading-diff 0.5)
             (state/sub-damage! (* heading-diff 3)))

           ;; if landed and alive, popup
           (when (pos? (:health @state/state))

             (popup/popup! (vec2/add pos2 (vec2/vec2 0 -30))
                           (rand-nth (flip-text flips))
                           200)

             (sound/play-sound :splash1 0.3 false)

             (when-let [sfx (flip-sfx flips)]
               (sound/play-sound sfx 0.5 false))

             (state/add-score! (flip-score flips))
             )


             ))

       (s/set-pos! player constrained-pos)
       (s/set-rotation! player heading)

       (<! (e/next-frame))

       (if (<= (:health @state/state) 0)
         ;; die
         (do
           (explosion/explosion player)
           (sound/play-sound :boom1 0.5 false)
           (<! (e/wait-frames 100))
           (m/with-sprite :ui
             [gameover (pf/make-text :small "Game Over" :scale 7)]
             (sound/play-sound :gameover 0.5 false)
             (<! (e/wait-frames 300)))
           (state/die!))

         ;; still living
         (recur (inc fnum)
                (vec2/add constrained-pos joy)
                vel
                heading
                heading-delta
                player-on-wave?

                (if player-on-wave?
                  0
                  (+ total-delta heading-delta))
                ))))
   ))

(defn slide-text [text-string]
  (go-while
   (not (state/playing?))
   (m/with-sprite :ui
     [text (pf/make-text :small text-string
                         :scale 3
                         :x 0 :y 150)]

     ;; Slide in
     (loop [fnum 0]
       (let [width (.-innerWidth js/window)
             height (.-innerHeight js/window)
             x-pos  (Math/pow 1.05 (- 150 fnum))]
         (s/set-x! text x-pos)

         (when (= 20 fnum) (sound/play-sound :text-arrive 0.5 false))

         (<! (e/next-frame))
         (when (> x-pos 1)
           (recur (inc fnum)))))

     (<! (e/wait-frames 30))

     ;; Slide out
     (loop [fnum 0]
       (let [width (.-innerWidth js/window)
             height (.-innerHeight js/window)
             x-pos  (- 0  (Math/pow 1.1 fnum))]
         (s/set-x! text x-pos)

         (when (= fnum 20) (sound/play-sound :text-depart 0.5 false))

         (<! (e/next-frame))
         (when (> x-pos -1000)
           (recur (inc fnum))))))))

(defn instructions-thread []
  (go-while (not (state/playing?))
    (let [instructions ["Press any button to play" "Pull some sik flips" "Do not sink your dingy"]]
      (loop [strings (cycle instructions)]
        (<! (slide-text (first strings)))
        (recur (rest strings))))))

(defn titlescreen-thread [tidal upsurge]
  (go-while
   (not (start-pressed?))
   (instructions-thread)
   (state/set-amp! 20)
   (loop [fnum 0]
     (let [
           {:keys [wave level-x]} @state/state
           {:keys [amp freq phase]} wave

           xpos (+ level-x phase)

           height (.-innerHeight js/window)
           width (.-innerWidth js/window)
           tidal-y-pos (wave-y-position width height amp freq xpos -200)
           tidal-heading (wave-theta width height amp freq xpos -200)

           upsurge-y-pos (wave-y-position width height amp freq xpos 200)
           upsurge-heading (wave-theta width height amp freq xpos 200)
           ]

       (s/set-pos! tidal -200 (+ tidal-y-pos -30))
       (s/set-rotation! tidal (/ tidal-heading 4))

       (s/set-pos! upsurge 200 (+ upsurge-y-pos -30))
       (s/set-rotation! upsurge (/ upsurge-heading 4))

       (<! (e/next-frame))
       (recur (inc fnum))))))

(defonce main
  (go                              ;-until-reload
                                        ;state
                                        ; load resource url with tile sheet
    (<! (r/load-resources canvas :ui ["img/spritesheet.png"
                                      "img/fonts.png"
                                      "sfx/jump1.ogg"
                                      "sfx/splash1.ogg"
                                      "sfx/splash-smooth.ogg"
                                      "sfx/boom1.ogg"
                                      "sfx/title-slide.ogg"
                                      "sfx/text-arrive.ogg"
                                      "sfx/text-depart.ogg"
                                      "sfx/game-start.ogg"
                                      "sfx/gameover.ogg"
                                      "sfx/land1.ogg"
                                      ]))

    (t/load-sprite-sheet!
     (r/get-texture :spritesheet :nearest)
     assets/sprites)

    (pf/pixel-font :small "img/fonts.png" [11 117] [235 169]
                   :chars ["ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                           "abcdefghijklmnopqrstuvwxyz"
                           "0123456789!?#`'.,-"]
                   :kerning {"fo" -2  "ro" -1 "la" -1 }
                   :space 5)

    (m/with-sprite :player
      [
       bg (s/make-sprite (make-background) :scale 100)
       tidal (s/make-sprite  :tidal :scale scale :x 0 :y 0)
       upsurge (s/make-sprite  :upsurge :scale scale :x 0 :y 0)
       player (s/make-sprite :boat
                             :scale scale
                             :x 0 :y 0)]
      (m/with-sprite-set :clouds
        [cloudset (clouds/get-sprites)]
        (clouds/cloud-thread cloudset)
        (js/console.log "cloudset" (str cloudset))

        (let [shader (wave-line [1 1])]

          (set-texture-filter bg shader)

          (wave-update-thread shader)
          (health-display-thread)

          (while true
            (s/set-visible! player false)
            (s/set-visible! tidal true)
            (s/set-visible! upsurge true)
            (<! (titlescreen-thread tidal upsurge))

            (s/set-visible! upsurge false)
            (s/set-visible! tidal false)
            (s/set-visible! player true)
            (<! (player-thread player)))
  )))))
