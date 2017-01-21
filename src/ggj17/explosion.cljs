(ns ggj17.explosion
  (:require [infinitelives.pixi.sprite :as s]
            [infinitelives.pixi.events :as e]
            [infinitelives.utils.vec2 :as vec2]
            [infinitelives.utils.console :refer [log]]
            [ggj17.state :as state])
  (:require-macros [infinitelives.pixi.macros :as m]
                   [cljs.core.async.macros :refer [go]])
  )

(def explosion-frames [:explode-1 :explode-2
                       :explode-3 :explode-4
                       :explode-5 :explode-6])

(def explosion-speed 6)

(defn explosion [entity]
  ;(sound/play-sound (keyword (str "explode-" (rand-int 10))) 0.5 false)
  (let [frameset explosion-frames]
    (go
      (let [initial-pos (s/get-pos entity)
            x (vec2/get-x initial-pos)
            y (vec2/get-y initial-pos)
            frames (count frameset)
            total-frames (* frames explosion-speed)
            ]
        (m/with-sprite :player
          [explosion (s/make-sprite (first frameset)
                                    :scale 4 :x x :y y
                                        ;:rotation (* (rand) Math/PI 2)
                                    )]
          (loop [n 0
                 pos initial-pos]
            (s/set-texture! explosion (get frameset (int (/ n explosion-speed)) (last frameset)))
            (s/set-pos! explosion pos)

            ;; when explosion is maximum size, we can disappear the
            ;; underlying entity
            (when (= 3 n)
              (s/set-visible! entity false))

            (<! (e/next-frame))

            (when (< n total-frames)
              (recur (inc n) pos))))))))
