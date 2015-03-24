package org.jenkinsci.plugins.vncviewer;

import hudson.console.HyperlinkNote;

public class VncHyperlinkNote extends HyperlinkNote {

	private static final long serialVersionUID = 1951864374194045781L;

	public VncHyperlinkNote(String url, int length)
	{
		super(url, length);
	}

	@Override
	protected String extraAttributes() {
		return "target=\"_blank\"";
	}

}
