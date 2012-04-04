package com.schlimm.java7.nio.investigation.closing.provider;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import sun.reflect.misc.MethodUtil;

import com.schlimm.java7.nio.investigation.closing.graceful.GracefulAsynchronousFileChannel;

/**
 * Expects safe URIs or safe Paths as Input
 * @author Niklas Schlimm
 *
 */
public class MyFileSystemProvider extends FileSystemProvider {

	private FileSystemProvider provider = FileSystems.getDefault().provider();

	@Override
	public String getScheme() {
		return "safe";
	}

	public AsynchronousFileChannel newAsynchronousFileChannel(Path file, Set<? extends OpenOption> options,
			ExecutorService executor, FileAttribute<?>... attrs) throws IOException {
		return GracefulAsynchronousFileChannel.get("file:/E:/temp/afile.out");
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		// TODO: implementieren
		try {
			return provider.newFileSystem(new URI(provider.getScheme()+uri.getSchemeSpecificPart()), env);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		// TODO: implementieren
		try {
			return provider.getFileSystem(new URI(provider.getScheme()+uri.getSchemeSpecificPart()));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Path getPath(URI uri) {
		try {
			final Path target = provider.getPath(new URI(provider.getScheme()+":"+uri.getSchemeSpecificPart()));
			return (Path)Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{Path.class},new InvocationHandler() {
				
				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					if (method.getName().equals("getFileSystem")) {
						final FileSystem targetFileSystem = new MyFileSystem();
						return targetFileSystem;
					}
					
					return MethodUtil.invoke(method, target, args);
				}
			});
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

	// TODO: alle safe paths umwandeln in file paths
	
	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		return provider.newByteChannel(path, options, attrs);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		return provider.newDirectoryStream(dir, filter);
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		provider.createDirectory(dir, attrs);
	}

	@Override
	public void delete(Path path) throws IOException {
		provider.delete(path);
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		provider.copy(source, target, options);
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		provider.move(source, target, options);
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		return provider.isSameFile(path, path2);
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		return provider.isHidden(path);
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return provider.getFileStore(path);
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		provider.checkAccess(path, modes);
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return provider.getFileAttributeView(path, type, options);
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		return provider.readAttributes(path, type, options);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return provider.readAttributes(path, attributes, options);
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		provider.setAttribute(path, attribute, value, options);
	}

}
