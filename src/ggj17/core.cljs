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
            [ggj17.game :as game]
            [ggj17.wave :as wave]
            [ggj17.splash :as splash]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   [infinitelives.pixi.pixelfont :as pf]
                   ))

(enable-console-print!)


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

(defn update-colours [shader sky-hue sea-hue]
  (set! (.-uniforms.skyHue.value shader) sky-hue)
  (set! (.-uniforms.seaHue.value shader) sea-hue)
  )


(defn set-texture-filter [texture filter]
  (set! (.-filters texture) (make-array filter)))

(defn start-pressed? []
  (or
   (e/is-pressed? :space)
   (gp/button-pressed? 0 :a)
   (gp/button-pressed? 0 :b)
   (gp/button-pressed? 0 :x)
   (gp/button-pressed? 0 :y)))

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
           tidal-y-pos (wave/wave-y-position width height amp freq xpos -200)
           tidal-heading (wave/wave-theta width height amp freq xpos -200)

           upsurge-y-pos (wave/wave-y-position width height amp freq xpos 200)
           upsurge-heading (wave/wave-theta width height amp freq xpos 200)
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

        (let [shader (wave/wave-line [1 1])]

          (set-texture-filter bg shader)

          (wave/wave-update-thread shader)
          (game/health-display-thread)

          (while true
            (s/set-visible! player false)
            (s/set-visible! tidal true)
            (s/set-visible! upsurge true)
            (<! (titlescreen-thread tidal upsurge))

            (s/set-visible! upsurge false)
            (s/set-visible! tidal false)
            (s/set-visible! player true)
            (<! (game/player-thread player)))
          )))))
