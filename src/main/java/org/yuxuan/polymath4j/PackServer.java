package org.yuxuan.polymath4j;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public final class PackServer {
	public final Polymath4J polymath4J;
	public final Logger logger;
	public final Gson gson;
	public final PackManager packManager;
	public Server server;

	public PackServer(Polymath4J polymath4J) {
		this.polymath4J = polymath4J;
		this.logger = polymath4J.logger;
		this.gson = new GsonBuilder().create();
		this.packManager = polymath4J.packManager;
	}

	public boolean start() {
		server = new Server(polymath4J.port);

		ServletContextHandler handler = new ServletContextHandler();
		handler.setErrorHandler(new ErrorHandler() {
			@Override
			public void doError(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {

			}
		});
		ServletHolder uploadHolder = new ServletHolder(new HttpServlet() {
			@Override
			protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				logger.log(Level.INFO, "Received upload request from " + req.getRemoteAddr());
				Part idPart = req.getPart("id");
				if (idPart == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
					return;
				}
				if (idPart.getSize() > 8192) {
					resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
					return;
				}
				Part packPart = req.getPart("pack");
				if (packPart == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
					return;
				}
				if (packPart.getSize() > polymath4J.maxSize) {
					resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
					return;
				}
				String id = IOUtils.toString(idPart.getInputStream(), StandardCharsets.UTF_8);
				byte[] pack = IOUtils.readFully(packPart.getInputStream(), (int) packPart.getSize());
				Optional<String> result = packManager.register(pack, id, req.getRemoteAddr());
				if (!result.isPresent()) {
					resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
					return;
				}
				String idHash = result.get();
				resp.setContentType("application/json; charset=utf-8");
				try (PrintWriter out = resp.getWriter()) {
					JsonObject json = new JsonObject();
					json.addProperty("url", polymath4J.url + "/pack.zip?id=" + idHash);
					json.addProperty("sha1", idHash);
					out.write(gson.toJson(json));
				}
			}
		});
		uploadHolder.getRegistration().setMultipartConfig(new MultipartConfigElement("multipart-tmp", polymath4J.maxSize, polymath4J.maxSize, Integer.MAX_VALUE));
		handler.addServlet(uploadHolder, "/upload");
		handler.addServlet(new ServletHolder(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				logger.log(Level.INFO, "Received download request from " + req.getRemoteAddr());
				String id = req.getParameter("id");
				if (id == null) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
					return;
				}
				Optional<File> optionalFile = packManager.fetch(id);
				if (!optionalFile.isPresent()) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				File file = optionalFile.get();
				if (!file.exists() || !file.isFile()) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				logger.log(Level.INFO, "Sending pack " + id + " to " + req.getRemoteAddr());
				resp.setContentType("application/zip");
				try (InputStream in = Files.newInputStream(file.toPath()); OutputStream out = resp.getOutputStream()) {
					IOUtils.copy(in, out);
				}
			}
		}), "/pack.zip");
		handler.addServlet(new ServletHolder(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				logger.log(Level.INFO, "Received test request from " + req.getRemoteAddr());
				try (PrintWriter out = resp.getWriter()) {
					out.write("It seems to be working...");
				}
			}
		}), "/debug");
		server.setHandler(handler);
		try {
			server.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Could not start http server", e);
			return false;
		}
		return true;
	}

	public void stop() {
		if (server != null) {
			try {
				if (server.isRunning()) {
					server.stop();
				}
				server = null;
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Could not stop http server", e);
			}
		}
	}
}