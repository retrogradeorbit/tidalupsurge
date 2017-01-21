(ns ggj17.state
  (:require [infinitelives.utils.vec2 :as vec2]))

(defonce state
  (atom
   {:wave
    {:amp 100
     :freq 0.005
     :phase 0
     :fnum 0}

    :pos (vec2/vec2 0 0)})
  )

(defn set-amp! [amp]
  (swap! state assoc-in [:wave :amp] amp)
  )
