# vncviewer-plugin
Jenkins vncviewer plugin

VncViewer lets you monitor or operate GUI of your running build. You can start HTML5 based VNC viewer via HTML link directly from 'Console output'. This plugin can be used in combination with Xvnc and VncRecorder plugins.

The plugin uses internally HTML5 based VNC client noVNC. 
Example for usage in pipeline:
stages {
        stage('Build') { 
            steps { 
                wrap([$class: 'Xvnc', takeScreenshot: false, useXauthority: true]) 
                {
                 wrap([$class: 'VncViewerBuildWrapper', vncServ: 'localhost:$DISPLAY']) 
                 {
                  sh doSomething..
                 }
                }
            }
        }
See https://plugins.jenkins.io/vncviewer/ for more details.
