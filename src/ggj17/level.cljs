(ns ggj17.level
  (:require
   [infinitelives.pixi.events :as e]
   [ggj17.state :as state]
   [ggj17.floaty :as floaty]
   )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]
                   [ggj17.async :refer [go-while go-until-reload]]
                   [infinitelives.pixi.pixelfont :as pf]
                   ))

(defn level-thread []
  (go
   (loop [fnum 0]
     (let [level-x (:level-x @state/state)
           amp (* 20
                  (* 2.5 (Math/sin (/ fnum 1820)))
                  (* 0.9 (Math/sin (/ fnum 355)))
                  (* 2.5 (Math/sin (/ fnum 2534)))
                  (* 2 (Math/sin (/ fnum 1456)))
                  (* 2 (Math/sin (/ fnum 5666))))

           freq (+ 0.005
                   (* 0.005
                      (* 2 (Math/abs (Math/sin (/ fnum 65436))))
                      (* 0.5 (Math/abs (Math/sin (/ fnum 34535))))
                      (* 3.2 (Math/abs (Math/sin (/ fnum 234234))))

                      ))]                     
       (state/set-amp! amp)
       (state/set-freq! freq)

       (when
           (or
            (zero? (dec (rem (int level-x) 300)))
            (zero? (dec (rem (int level-x) 555)))
            (zero? (dec (rem (int level-x) 2432)))
            (zero? (dec (rem (int level-x) 1234)))
            )
         (floaty/spawn-floaty! (+ level-x 30 (/ (.-innerWidth js/window) 2))))

       (<! (e/next-frame))
       (when (state/playing?)
         (recur (inc fnum)))))))
