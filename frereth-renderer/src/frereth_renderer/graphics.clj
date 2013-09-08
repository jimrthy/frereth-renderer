(ns frereth-renderer.graphics
  (#_ (:import [javax.media.opengl GLAutoDrawable GLEventListener GLProfile
                GLCapabilities]
               javax.media.opengl.awt.GLCanvas
               java.awt.Frame
               [java.awt.event WindowAdapter WindowEvent]))
  ;; TODO: If I'm going to take this approach, definitely want to have
  ;; different options about which OpenGL version to 
  (:import [org.lwjgl.opengl Display DisplayMode GL11]
           ;; Very torn about using GLU. It seems like a mistake.
           [org.lwjgl.util.glu GLU])
  (:gen-class))

(defn driver-version
  "What's available?"
  []
  (GL11/glGetString GL11/GL_VERSION))

(defn init-window
  "Initialize the static window where absolutely everything interesting will happen.
This approach is more than a little dumb...should really be able to create full-screen
windows that fill multiple monitors.
Baby steps. I'm just trying to get that rope thrown across the gorge."
  [{:keys [width height title]}]
  (Display/setDisplayMode (DisplayMode. width height))
  (Display/setTitle title)
  (Display/create))

(defn init-gl
  "Set up the basic framework for rendering.
This is *definitely* not a long-term thing. Each world should be
specifying its own viewport. Most games will probably want multiple
modes for a HUD.
Baby steps."
  [{:keys [width height]}]
  (GL11/glClearColor 0.5 0.0 0.5 0.0)
  (GL11/glMatrixMode GL11/GL_PROJECTION)
  ;; *Definitely* have mixed feelings about using GLU. Isn't that
  ;; basically deprecated?
  (GLU/gluOrtho2D 0.0 width
                  0.0 height)
  (GL11/glMatrixMode GL11/GL_MODELVIEW))

(defn build-display
  "Set up the 'main' window."
  [params]
  (init-window params)
  (init-gl params))

(defn draw
  [{:keys [width height angle]}]
  (let [w2 (/ width 2.0)
        h2 (/ height 2.0)]
    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT  GL11/GL_DEPTH_BUFFER_BIT))
    
    (GL11/glLoadIdentity)
    (GL11/glTranslatef w2 h2 0)
    (GL11/glRotatef angle 0 0 1)
    (GL11/glScalef 2 2 1)
    (GL11/glBegin GL11/GL_TRIANGLES)
    (do
      (GL11/glColor3f 1.0 0.0 0.0)
      (GL11/glVertex2i 100 0)
      (GL11/glColor3f 0.0 1.0 0.0)
      (GL11/glVertex2i -50 86.6)
      (GL11/glColor3f 0.0 0.0 1.0)      
      (GL11/glVertex2i -50 -86.6)
      )
    (GL11/glEnd)))

(defn update-initial-splash
  [{:keys [width height angle last-time]} :as params]
  (let [cur-time (System/currentTimeMillis)
        delta-time (- cur-time last-time)
        next-angle (+ (* delta-time 0.05) angle)
        next-angle (if (>= next-angle 360.0)
                     (- next-angle 360.0)
                     next-angle)
        result (into params :angle next-angle :last-time cur-time)]
    (draw result)
    result))

(defn fps->millis
  "Silly utilitiy function. At x frames per second, each individual
frame should be on the screen for y milliseconds."
  [x]
  (* (/ 1 f) 1000))

(defn run-initial-splash
  "Show an initial splash screen while we're waiting to connect to a client"
  [{:keys [controller]} :as params]
  ;; Try to read from controller. Use an alt with a 60 FPS timeout.
  ;; This function shouldn't last long at all.
  (let [frame-length  (fps->millis 60)]
    (loop [params params]
      (when (not (Display/isCloseRequested))
        (let [updated-params (update-initial-splash params)]
             (Display/update)
             ;; TODO: Do I really want to create a new timeout each frame?
             (throw (RuntimeException. "Get that answered"))
             (let [next-frame (timeout frame-length)]
               (let [[value channel] 
                     (alts!! [controller next-frame])]
                 ;; If the channel actually closed, something bad has happened.
                 ;; TODO: is that true?
                 ;; I expect this to mostly timeout. What is value then?
                 (if (= channel next-frame)
                   (do
                     ;; Waiting
                     ;; TODO: Need to update the angle associated with params.
                     (recur update-params))
                   (do
                     (assert (= value :sterile-environment))
                     ;; In all honesty, I want to keep doing exactly the same
                     ;; thing.
                     ;; Except that update-initial-splash should start doing
                     ;; something more interesting, and I'm waiting on a
                     ;; different value.
                     (throw (RuntimeException. "What next?")))))))))))
