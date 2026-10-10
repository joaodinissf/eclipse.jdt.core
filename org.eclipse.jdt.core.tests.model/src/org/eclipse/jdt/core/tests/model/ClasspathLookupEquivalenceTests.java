/*******************************************************************************
 * Copyright (c) 2026 João Dinis Ferreira and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.core.tests.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.Test;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.ClasspathChange;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaProject;

/**
 * Checks that the path-indexed lookup in {@link ClasspathChange} answers exactly what the
 * original linear scan answered. {@link #referenceClasspathContains} is the pre-index
 * implementation, kept verbatim as the oracle.
 */
public class ClasspathLookupEquivalenceTests extends AbstractJavaModelTests {

	public ClasspathLookupEquivalenceTests(String name) {
		super(name);
	}

	public static Test suite() {
		return buildModelTestSuite(ClasspathLookupEquivalenceTests.class);
	}

	private static int referenceClasspathContains(IJavaProject project, IClasspathEntry[] list, IClasspathEntry entry) {
		IPath[] exclusionPatterns = entry.getExclusionPatterns();
		IPath[] inclusionPatterns = entry.getInclusionPatterns();
		int listLen = list == null ? 0 : list.length;
		nextEntry: for (int i = 0; i < listLen; i++) {
			IClasspathEntry other = list[i];
			if (other.getContentKind() == entry.getContentKind()
				&& other.getEntryKind() == entry.getEntryKind()
				&& other.isExported() == entry.isExported()
				&& other.getPath().equals(entry.getPath())) {
					IPath entryOutput = entry.getOutputLocation();
					IPath otherOutput = other.getOutputLocation();
					if (entryOutput == null) {
						if (otherOutput != null)
							continue;
					} else {
						if (!entryOutput.equals(otherOutput))
							continue;
					}
					IPath[] otherIncludes = other.getInclusionPatterns();
					if (inclusionPatterns != otherIncludes) {
						if (inclusionPatterns == null) continue;
						int includeLength = inclusionPatterns.length;
						if (otherIncludes == null || otherIncludes.length != includeLength)
							continue;
						for (int j = 0; j < includeLength; j++) {
							if (!inclusionPatterns[j].toString().equals(otherIncludes[j].toString()))
								continue nextEntry;
						}
					}
					IPath[] otherExcludes = other.getExclusionPatterns();
					if (exclusionPatterns != otherExcludes) {
						if (exclusionPatterns == null) continue;
						int excludeLength = exclusionPatterns.length;
						if (otherExcludes == null || otherExcludes.length != excludeLength)
							continue;
						for (int j = 0; j < excludeLength; j++) {
							if (!exclusionPatterns[j].toString().equals(otherExcludes[j].toString()))
								continue nextEntry;
						}
					}
					if (JavaCore.ENABLED.equals(project.getOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, true))) {
						String annotationPath = ClasspathEntry.getExtraAttribute(entry, IClasspathAttribute.EXTERNAL_ANNOTATION_PATH);
						String otherAnnotationPath = ClasspathEntry.getExtraAttribute(other, IClasspathAttribute.EXTERNAL_ANNOTATION_PATH);
						if (annotationPath != null && otherAnnotationPath != null) {
							if (!annotationPath.equals(otherAnnotationPath))
								continue;
						} else if (annotationPath != otherAnnotationPath) {
							continue;
						}
					}
					if (((ClasspathEntry) entry).isModular() != ((ClasspathEntry) other).isModular()) {
						continue nextEntry;
					}
					return i;
			}
		}
		return -1;
	}

	private static IClasspathAttribute[] attrs(String... nameValues) {
		IClasspathAttribute[] result = new IClasspathAttribute[nameValues.length / 2];
		for (int i = 0; i < result.length; i++) {
			result[i] = JavaCore.newClasspathAttribute(nameValues[2 * i], nameValues[2 * i + 1]);
		}
		return result;
	}

	private static IClasspathEntry lib(String path, boolean exported, IClasspathAttribute... attributes) {
		return JavaCore.newLibraryEntry(new Path(path), null, null, null, attributes, exported);
	}

	private static IClasspathEntry src(String path, String output, String[] includes, String[] excludes) {
		IPath[] in = new IPath[includes.length];
		for (int i = 0; i < includes.length; i++) {
			in[i] = new Path(includes[i]);
		}
		IPath[] ex = new IPath[excludes.length];
		for (int i = 0; i < excludes.length; i++) {
			ex[i] = new Path(excludes[i]);
		}
		return JavaCore.newSourceEntry(new Path(path), in, ex, output == null ? null : new Path(output));
	}

	private List<IClasspathEntry> entries() {
		List<IClasspathEntry> e = new ArrayList<>();
		e.add(lib("/ext/a.jar", false));
		e.add(lib("/ext/a.jar/", false)); // same IPath as /ext/a.jar: equals and hashCode ignore the trailing separator
		e.add(lib("/ext/a.jar", true));
		e.add(lib("/ext/a.jar", false, attrs(IClasspathAttribute.EXTERNAL_ANNOTATION_PATH, "/ann/a")));
		e.add(lib("/ext/a.jar", false, attrs(IClasspathAttribute.EXTERNAL_ANNOTATION_PATH, "/ann/b")));
		e.add(lib("/ext/a.jar", false, attrs(IClasspathAttribute.MODULE, "true")));
		e.add(lib("/ext/b.jar", false));
		e.add(JavaCore.newProjectEntry(new Path("/ext/a.jar"))); // same path, different kind
		e.add(JavaCore.newProjectEntry(new Path("/X")));
		e.add(src("/P/src", null, new String[0], new String[0]));
		e.add(src("/P/src", "/P/bin2", new String[0], new String[0]));
		e.add(src("/P/src", null, new String[] { "x/" }, new String[0]));
		e.add(src("/P/src", null, new String[] { "x" }, new String[0])); // patterns compare by toString: "x/" != "x"
		e.add(src("/P/src", null, new String[0], new String[] { "**/gen/**" }));
		e.add(src("/P/src", null, new String[0], new String[] { "**/gen/**", "**/tmp/**" }));
		return e;
	}

	private List<IClasspathEntry[]> lists(List<IClasspathEntry> e) {
		List<IClasspathEntry[]> lists = new ArrayList<>();
		lists.add(null);
		lists.add(new IClasspathEntry[0]);
		lists.add(e.toArray(new IClasspathEntry[0]));
		List<IClasspathEntry> reversed = new ArrayList<>(e);
		Collections.reverse(reversed);
		lists.add(reversed.toArray(new IClasspathEntry[0]));
		// duplicates: first-match order must be preserved
		lists.add(new IClasspathEntry[] { e.get(2), e.get(0), e.get(1), e.get(0), e.get(5) });
		lists.add(new IClasspathEntry[] { e.get(4), e.get(3), e.get(7), e.get(0) });
		lists.add(new IClasspathEntry[] { e.get(11), e.get(12), e.get(9), e.get(13), e.get(14), e.get(10) });
		return lists;
	}

	private void assertEquivalent(boolean nullAnalysis) throws Exception {
		JavaProject project = (JavaProject) getJavaProject("P");
		project.setOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, nullAnalysis ? JavaCore.ENABLED : JavaCore.DISABLED);
		ClasspathChange change = new ClasspathChange(project, null, null, null);

		Method linear = ClasspathChange.class.getDeclaredMethod("classpathContains", IClasspathEntry[].class,
				IClasspathEntry.class);
		linear.setAccessible(true);
		Class<?> lookupClass = Class.forName(ClasspathChange.class.getName() + "$ClasspathLookup");
		Constructor<?> lookupCtor = lookupClass.getDeclaredConstructor(ClasspathChange.class, IClasspathEntry[].class);
		lookupCtor.setAccessible(true);
		Method indexOf = lookupClass.getDeclaredMethod("indexOf", IClasspathEntry.class);
		indexOf.setAccessible(true);
		Method firstWithPath = lookupClass.getDeclaredMethod("firstWithPath", IPath.class);
		firstWithPath.setAccessible(true);

		List<IClasspathEntry> probes = entries();
		probes.add(lib("/ext/absent.jar", false));
		int compared = 0;
		for (IClasspathEntry[] list : lists(entries())) {
			Object lookup = lookupCtor.newInstance(change, (Object) list);
			for (IClasspathEntry probe : probes) {
				String context = "nullAnalysis=" + nullAnalysis + ", probe=" + probe;
				int expected = referenceClasspathContains(project, list, probe);
				assertEquals("classpathContains: " + context, expected, ((Integer) linear.invoke(change, list, probe)).intValue());
				assertEquals("ClasspathLookup.indexOf: " + context, expected, ((Integer) indexOf.invoke(lookup, probe)).intValue());

				IClasspathEntry firstLinear = null;
				for (int i = 0; list != null && i < list.length; i++) {
					if (list[i].getPath().equals(probe.getPath())) {
						firstLinear = list[i];
						break;
					}
				}
				assertSame("ClasspathLookup.firstWithPath: " + context, firstLinear, firstWithPath.invoke(lookup, probe.getPath()));
				compared++;
			}
		}
		assertTrue("too few comparisons: " + compared, compared > 100);
	}

	public void testEquivalenceNullAnalysisDisabled() throws Exception {
		try {
			createJavaProject("P", new String[] {}, new String[] {}, "");
			assertEquivalent(false);
		} finally {
			deleteProject("P");
		}
	}

	public void testEquivalenceNullAnalysisEnabled() throws Exception {
		try {
			createJavaProject("P", new String[] {}, new String[] {}, "");
			assertEquivalent(true);
		} finally {
			deleteProject("P");
		}
	}
}
