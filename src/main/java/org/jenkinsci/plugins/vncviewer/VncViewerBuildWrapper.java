/*
 * Copyright (c) 2015 Dimitri Tenenbaum All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package org.jenkinsci.plugins.vncviewer;
import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Proc;
import hudson.Util;
import hudson.console.ExpandableDetailsNote;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.lang.SystemUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


public class VncViewerBuildWrapper extends BuildWrapper {
	private String vncServ;
	private int localPort = 8888;
	private Proc noVncProc;
	private ByteArrayOutputStream loggingStream;

	@DataBoundConstructor
	public VncViewerBuildWrapper(String vncServ) 
	{
		this.vncServ = vncServ;
		//		this.vncServ = "10.137.12.222:5900"; //TODO
	}

	public String getVncServ() {
		return vncServ;
	}

	public void setVncServ(String vncServ) {
		this.vncServ = vncServ;
	}

	private void startNoVnc(AbstractBuild build, BuildListener listener, Launcher launcher) throws Exception 
	{
		String webSockifyPath = System.getProperty("java.io.tmpdir") + File.separator + "websockify" + File.separator + "websockify.py";
		File f = new File(webSockifyPath);
		if (!f.canExecute())
		{
			f.setExecutable(true);
		}
		String webPath = System.getProperty("java.io.tmpdir") + File.separator + "novnc";
		PrintStream logger = listener.getLogger();
		LocalLauncher localLauncher = new LocalLauncher(listener);
		loggingStream = new ByteArrayOutputStream();
		for (int i = 0; i < 1000 ; i++ )
		{
			noVncProc = localLauncher.launch().stderr(loggingStream).stdout(loggingStream).cmds(webSockifyPath, "--web", webPath,String.valueOf(localPort + i),getVncServ()).start();
			Thread.sleep(3000);
			if (noVncProc.isAlive())
			{
				break;
			}
			else
			{
				try {noVncProc.kill();}catch (Exception e){} 
			}
		}
	}

	@Override
	public Environment setUp(@SuppressWarnings("rawtypes")AbstractBuild build, Launcher launcher,
			final BuildListener listener) throws IOException, InterruptedException
	{
		DescriptorImpl DESCRIPTOR = Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
		vncServ = Util.replaceMacro(vncServ,build.getEnvironment(listener));
		if (vncServ.isEmpty())
			vncServ = DESCRIPTOR.getDefaultVncServ();

		if (vncServ.indexOf(":") < 0)
		{
			vncServ += ":5900";
		}
		try {
			untar(VncViewerBuildWrapper.class.getResourceAsStream("/novnc.tar"),System.getProperty("java.io.tmpdir"));
			untar(VncViewerBuildWrapper.class.getResourceAsStream("/websockify.tar"),System.getProperty("java.io.tmpdir"));
			startNoVnc(build,listener,launcher);
		} catch (Exception e) {
			e.printStackTrace();
		}

		String hostAddr = InetAddress.getLocalHost().getHostAddress();
		String url = "http://" + hostAddr + ":" + localPort + "/vnc_auto.html?host=" + hostAddr + "&port=" + localPort;
		String txt = "Start vnc viewer for " + vncServ;
		listener.getLogger().print('\n');
		listener.annotate(new VncHyperlinkNote(url,txt.length()));
		listener.getLogger().print(txt);
		listener.getLogger().print("\n\n");

		return new Environment() {
			@Override
			public void buildEnvVars(Map<String, String> env) {
				//				env.put("PATH",env.get("PATH"));
				//				env.put("DISPLAY", vncServ);
			}
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener)
					throws IOException, InterruptedException {


				try {

					ExpandableDetailsNote dn = new ExpandableDetailsNote("VNC viewer logs",loggingStream.toString());
					listener.annotate(dn);
					listener.getLogger().print('\n');
					noVncProc.kill();
					loggingStream.close();
				}
				catch (Exception e)
				{} 
				return true;
			}
		};
	}

	@Extension(ordinal = -1)
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		public DescriptorImpl() {
			super(VncViewerBuildWrapper.class);
			load();
		}


		@Override
		public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
			req.bindJSON(this,json);
			save();
			return true;
		}

		public FormValidation doCheckVncServ(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value ) {
			if(!project.hasPermission(Item.CONFIGURE)){
				return FormValidation.ok();
			}

			if (value.isEmpty())
			{
				return FormValidation.errorWithMarkup("Vnc server can't be empty!" );
			}
			return FormValidation.okWithMarkup("<strong><font color=\"blue\">Please, make sure that your vncserer is running on '" + value  + "'</font></strong>");
		}

		@Override
		public String getDisplayName() {
			return "Enable VNC viewer";
		}

		public String getDefaultVncServ()
		{
			return "localhost:5900";
		}

		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return !SystemUtils.IS_OS_WINDOWS;
		}
	}

	public static void untar(InputStream is, String dest) throws IOException {
		//		  logger.info("Untar {}.",tarFile.getAbsolutePath());
		TarInputStream tarIn = new TarInputStream(is);
		try {
			TarEntry tarEntry = tarIn.getNextEntry();

			while (tarEntry != null) {// create a file with the same name as the tarEntry
				File destPath = new File(dest, tarEntry.getName());
				if (!destPath.exists())
					if (tarEntry.isDirectory()) 
					{
						destPath.mkdirs();
					} else 
					{
						destPath.createNewFile();
						byte [] btoRead = new byte[1024];
						BufferedOutputStream bout = 
								new BufferedOutputStream(new FileOutputStream(destPath));
						int len = 0;

						while((len = tarIn.read(btoRead)) != -1)
						{
							bout.write(btoRead,0,len);
						}

						bout.close();
						btoRead = null;
					}
				tarEntry = tarIn.getNextEntry();
			}
		}
		finally {
			tarIn.close();
		}
	}
}


