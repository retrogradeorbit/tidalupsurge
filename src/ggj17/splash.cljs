(ns ggj17.splash
  (:require [infinitelives.pixi.sprite :as s]
            [infinitelives.pixi.events :as e]
            [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.console :refer [log]]
            [ggj17.state :as state]
            [ggj17.wave :as wave])
  (:require-macros [infinitelives.pixi.macros :as m]
                   [cljs.core.async.macros :refer [go]])
  )

(def splash-frames [:splash-1
                    :splash-2
                    :splash-3
                    :splash-4
                    :splash-5
                    :splash-6])

(def splash-speed 4)

(def splash-scale 4)

(defn splash [initial-pos]
                                        ;(sound/play-sound (keyword (str "explode-" (rand-int 10))) 0.5 false)
  (log "XP:" initial-pos)

  (let [frameset splash-frames]
    (go
      (let [
            x (vec2/get-x initial-pos)
            y (vec2/get-y initial-pos)
            frames (count frameset)
            total-frames (* frames splash-speed)
            {:keys [wave level-x]} @state/state
            {:keys [amp freq phase fnum]} wave
            ]
        (m/with-sprite :player
          [splash (s/make-sprite (first frameset)
                                    :scale splash-scale :x x :y y
                                    )]
          (loop [n 0]

			(let [x-pos x
                  wave-x-pos (+ phase level-x)

                  y-pos  (wave/wave-y-position
				   (.-innerWidth js/window)
				   (.-innerHeight js/window)
				   amp freq wave-x-pos (- x-pos wave-x-pos))]

              (s/set-texture! splash (get frameset (int (/ n splash-speed)) (last frameset)))
              (s/set-pos! splash (- x-pos (:level-x @state/state)) y-pos))

            (<! (e/next-frame))

            (when (< n total-frames)
              (recur (inc n)))))))))
