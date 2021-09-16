package org.jrd.backend.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NestedJarsTest {

	private File f;
	ArchiveManager am = ArchiveManager.getInstance();

	@BeforeEach
	void setup() throws URISyntaxException {
		URL resource = getClass().getClassLoader().getResource("tester.zip");
		if (resource == null) {
			throw new IllegalArgumentException("file not found!");
		} else {
			f = new File(resource.toURI());
		}
	}

	@Test
	void nestedJarsSearchTest() throws IOException {
		assertTrue(am.isClassInFile("MainNotNested", f));
		assertTrue(am.isClassInFile("bye2.reallyNestedClass", f));
		assertTrue(am.isClassInFile("eu.tester.reader.NestedPseudoReader", f));
		assertTrue(am.isClassInFile("help.NestedReaderInHelp", f));
		assertTrue(am.isClassInFile("tester.MainToTestOnInDir", f));

		assertFalse(am.isClassInFile("WillFail", f));
		assertFalse(am.isClassInFile("ReaderHelp", f));
		assertFalse(am.isClassInFile("tester.", f));
	}

	@Test
	void shouldOpenTests() throws IOException {
		assertTrue(ArchiveManager.shouldOpen(".zip"));
		assertTrue(ArchiveManager.shouldOpen(".jar"));
		assertTrue(ArchiveManager.shouldOpen(".war"));
		assertTrue(ArchiveManager.shouldOpen(".ear"));

		ArrayList<String> test = new ArrayList<String>();
		test.add(".test");
		ArchiveManagerOptions.getInstance().setExtension(test);

		assertFalse(ArchiveManager.shouldOpen(".zip"));
		assertFalse(ArchiveManager.shouldOpen(".jar"));
		assertFalse(ArchiveManager.shouldOpen(".war"));
		assertFalse(ArchiveManager.shouldOpen(".ear"));

		assertTrue(ArchiveManager.shouldOpen(".test"));

		test.clear();
		ArchiveManagerOptions.getInstance().setExtension(test);
	}

	@Test
	void needExtractTests() throws IOException {
		assertTrue(am.isClassInFile("MainNotNested", f));
		assertFalse(am.needExtract());

		assertTrue(am.isClassInFile("bye2.reallyNestedClass", f));
		assertTrue(am.needExtract());

		assertTrue(am.isClassInFile("eu.tester.reader.NestedPseudoReader", f));
		assertTrue(am.needExtract());

		assertTrue(am.isClassInFile("help.NestedReaderInHelp", f));
		assertTrue(am.needExtract());

		assertTrue(am.isClassInFile("tester.MainToTestOnInDir", f));
		assertFalse(am.needExtract());
	}

	@Test
	void unpackTests() throws IOException {
		assertTrue(am.isClassInFile("bye2.reallyNestedClass", f));
		File tmp = am.unpack(f);
		assertTrue(tmp.getAbsolutePath().endsWith("3/bye/fuuu3/fuuu2/fuuu/reallyNested.zip") || tmp.getAbsolutePath().endsWith("3\\bye\\fuuu3\\fuuu2\\fuuu\\bye2.zip"));

		assertTrue(am.isClassInFile("eu.tester.reader.NestedPseudoReader", f));
		tmp = am.unpack(f);
		assertTrue(tmp.getAbsolutePath().endsWith("0/reader.jar") || tmp.getAbsolutePath().endsWith("0\\reader.jar"));
		am.delete();
	}

	@Test
	void packTests() throws IOException {
		String separator = System.getProperty("file.separator");
		assertTrue(am.isClassInFile("eu.tester.reader.NestedPseudoReader", f));
		File tmp = am.unpack(f);
		assertTrue(tmp.getAbsolutePath().endsWith("0" + separator + "reader.jar"));
		File tmp2 = new File(tmp.getParent() + separator + "test.txt");
		tmp2.createNewFile();
		tmp2 = new File(System.getProperty("java.io.tmpdir") + separator + "test2.zip");
		tmp2.createNewFile();
		am.pack(tmp2);
		am.delete();

		am.isClassInFile("help.NestedReaderInHelp", tmp2);
		assertTrue(am.isClassInFile("eu.tester.reader.NestedPseudoReader", tmp2));
		tmp = am.unpack(tmp2);
		tmp2 = new File(tmp.getParent());
		ArrayList<String> names = new ArrayList<>();
		for (File l : tmp2.listFiles()) {
			names.add(l.getName());
		}
		assertTrue(names.contains("test.txt"));
	}
}
