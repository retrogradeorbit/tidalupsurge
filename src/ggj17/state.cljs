(ns ggj17.state
  (:require [infinitelives.utils.vec2 :as vec2]))

(defonce state
  (atom
   {:wave
    {:amp 100
     :freq 0.005
     :phase 0
     :fnum 0}

    :pos (vec2/vec2 0 0)

    :health 100.0
    })
  )

(defn set-amp! [amp]
  (swap! state assoc-in [:wave :amp] amp)
  )

(defn set-health [state health]
  (assoc state :health health))

(defn set-health! [health]
  (swap! state set-health health))

(defn playing? []
  true)

(defn sub-damage [state damage]
  (update state :health - damage))

(defn sub-damage! [damage]
  (swap! state sub-damage damage))
