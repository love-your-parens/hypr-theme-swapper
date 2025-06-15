(ns theme-swapper
  (:require [babashka.fs :as fs]
            [babashka.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def cli-spec {:spec
               {:once {:coerce :boolean
                       :desc "Instructs the program to run once instead of looping."}}})
(def cli-opts (cli/parse-opts *command-line-args* cli-spec))
(def sh (comp process/check process/sh))
(def config-file (fs/file (.getParent (fs/file *file*)) "config.edn"))
(def config (or (when (fs/exists? config-file)
                  (edn/read (java.io.PushbackReader. (io/reader config-file))))
                {}))
(def wallpaper-directories (get config :wallpaper-directories []))
(def swap-interval (get config :swap-interval 300))

(defn picture? [file]
  (some-> file
          fs/extension
          string/lower-case
          #{"jpg" "jpeg" "png" "bmp" "webp"}))

(def wallpapers
  (fs/list-dirs wallpaper-directories picture?))

(def previous-wallpaper (atom nil))

(defn random-wallpaper []
  (when (seq wallpapers)
    (let [w (rand-nth wallpapers)]
      (if (and (> (count wallpapers) 1)
               (= w @previous-wallpaper))
        (recur)
        (reset! previous-wallpaper w)))))

(defn swap-theme []
  (when-let [wallpaper (random-wallpaper)]
    (try (sh ["matugen" "image" wallpaper])
         (sh ["killall" "-SIGINT" "walker"])
         (sh ["systemctl" "--user" "reload" "swaync"])

         ;; Currently taken care of by Matugen.
         ;; (sh ["swww" "img" "--transition-fps=240" wallpaper])

         (catch Exception e
           (log/warn (.getMessage e))))))

(defn sleep []
  (System/gc)
  (Thread/sleep (* swap-interval 1000)))

;;;; Program loop.
(when (= *file* (System/getProperty "babashka.file")) ; running as a script
  (if (:once cli-opts)
    (swap-theme)
    (do
      (log/info "Launching the theme swapper. Swap interval:" swap-interval "seconds.")
      (when (:defer cli-opts) (sleep))
      (loop []
        (log/info "Swapping theme...")
        (swap-theme)
        (sleep)
        (recur)))))

;;;; Notes

;;; Reloading Waybar
;; Below are the two clean ways of forcing Waybar to reload its configuration.
;; Neither of these is necessary here because hot-reloading CSS is supported.
;; See: `reload_style_on_change`
(comment
  (sh ["killall" "-SIGUSR2" "waybar"])
  (sh ["systemctl" "--user" "reload" "waybar"]))

;;; Hyprland flickering
;; Hyprland hot-reloads its configurations, so it doesn't require any handling besides
;; receiving a new color-definition file - which Matugen happily provides. Sadly, this
;; produces unpleasant side-effects. Namely:
;;
;; - the desktop flashes - atypical for Hyprland which normally employs smooth transitions
;; - swww's transition is often cut short or omitted wholesale
;;
;; Of note: this issue will also occur when the color-definition is replaced wholesale via copy.
;; It seems to happen less frequently when commiting edits to the file directly.
;;
;; Presumed causes:
;; x The theme definition is too long for Hyprland to grok efficiently.
;;   Doesn't seem to hold - flickering still happens with just three definitions.
;; x Hypr reacts to intermediate writes while the file is being written.
;;   How to prevent this? flock won't prevent reads. Maybe chmod instead?
;;   Again - even direct writes to the main config seem to cause this, so probably not it.
;; x Theme configuration is sourced at the tail end of Hypr's configs (as part of the config.d chain).
;;   Could this affect the scope of hot-reload in some way? No, happens even at the top of the config.
;;
;; It might just be a quirk of Hyprland's hot reload, in which case there's nothing to do but to live with it.
