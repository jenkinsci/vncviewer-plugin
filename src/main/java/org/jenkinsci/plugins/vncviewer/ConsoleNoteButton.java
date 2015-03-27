package org.jenkinsci.plugins.vncviewer;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import hudson.console.HyperlinkNote;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsoleNoteButton extends ConsoleNote {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final String caption;
    private final String html;

    public ConsoleNoteButton(String caption, String html) {
        this.caption = caption;
        this.html = html;
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(charPos,
//                "<input type=button value='"+caption+"' onClick='reveal-expandable-detail'><div class='expandable-detail'>"+html+"</div>");
        "<a href=\""+  html + "\" target=\"new\"><button>" + caption + "</button></a>");
        return null;
    }

    public static String encodeTo(String buttonCaption, String html) {
        try {
            return new ConsoleNoteButton(buttonCaption, html).encode();
        } catch (IOException e) {
            // impossible, but don't make this a fatal problem
            LOGGER.log(Level.WARNING, "Failed to serialize "+HyperlinkNote.class,e);
            return "";
        }
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Button";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ConsoleNoteButton.class.getName());
}