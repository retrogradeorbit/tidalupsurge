(ns ggj17.state
  (:require [infinitelives.utils.vec2 :as vec2]))

(def base-sky-colour 0.65)
(def base-sea-colour 0.65)

(defonce state
  (atom
   {:colours
    {:sky-hue base-sky-colour
     :sea-hue base-sea-colour}
    :wave
    {:amp 100
     :freq 0.005
     :phase 0
     :fnum 0}

    :pos (vec2/vec2 0 0)
    :level-x 0

    :health 100.0
    :score 0
    })
  )

(defn set-phase [state phase]
  (assoc-in state [:wave :phase] phase))

(defn set-phase! [phase]
  (swap! state set-phase phase))

(defn set-fnum [state fnum]
  (assoc-in state [:wave :fnum] fnum))

(defn set-fnum! [fnum]
  (swap! state set-fnum fnum))

(defn set-amp! [amp]
  (swap! state assoc-in [:wave :amp] amp))

(defn set-freq! [freq]
  (swap! state assoc-in [:wave :freq] freq))

(defn set-level-x! [x]
  (swap! state assoc :level-x x))

(defn get-sky-hue []
  (get-in @state [:colours :sky-hue]))

(defn get-sea-hue []
  (get-in @state [:colours :sea-hue]))

(defn set-hue [state thing hue]
  (assoc-in state [:colours thing] hue)
  )

(defn set-sky-hue [hue]
  (swap! state set-hue :sky-hue hue))

(defn set-sea-hue [hue]
  (swap! state set-hue :sea-hue hue))

(defn set-health [state health]
  (assoc state :health health))

(defn get-health []
  (:health @state))

(defn set-health! [health]
  (swap! state set-health health))

(defn playing? []
  (:playing? @state))

(defn sub-damage [state damage]
  (update state :health - damage))

(defn sub-damage! [damage]
  (swap! state sub-damage damage))

(defn start-game! []
  (swap! state assoc
         :health 100
         :pos (vec2/vec2 0 0)
         :playing? true
         :score 0))

(defn die! []
  (swap! state assoc :playing? false))

(defn add-score [state score]
  (update state :score + score))

(defn add-score! [score]
  (swap! state add-score score))
