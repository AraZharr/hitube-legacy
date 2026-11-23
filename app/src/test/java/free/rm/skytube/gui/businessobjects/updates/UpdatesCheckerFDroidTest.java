/*
 * SkyTube
 * Copyright (C) 2026  SkyTube Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package free.rm.skytube.gui.businessobjects.updates;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the HiTube update checker, which asks the GitHub API for the
 * latest release (this file used to test the old F-Droid updater, removed
 * in v1.0.9). Only the pure version comparison is unit-tested here; the
 * network fetch in {@link UpdatesChecker#checkForUpdates()} is not covered.
 */
class UpdatesCheckerTest {

	@Test
	void testNewerPatchVersion() {
		assertTrue(UpdatesChecker.isNewerVersion("1.0.9", "1.0.7"));
	}

	@Test
	void testOlderVersionIsNotNewer() {
		assertFalse(UpdatesChecker.isNewerVersion("1.0.7", "1.0.9"));
	}

	@Test
	void testSameVersionIsNotNewer() {
		assertFalse(UpdatesChecker.isNewerVersion("1.0.9", "1.0.9"));
	}

	@Test
	void testDoubleDigitPatchComparesNumerically() {
		// "1.0.10" must be newer than "1.0.9" (plain string comparison would say otherwise).
		assertTrue(UpdatesChecker.isNewerVersion("1.0.10", "1.0.9"));
		assertFalse(UpdatesChecker.isNewerVersion("1.0.9", "1.0.10"));
	}

	@Test
	void testLeadingVIsIgnored() {
		assertTrue(UpdatesChecker.isNewerVersion("v1.0.9", "1.0.8"));
		assertFalse(UpdatesChecker.isNewerVersion("1.0.9", "v1.0.9"));
	}

	@Test
	void testDifferentLengthVersions() {
		assertTrue(UpdatesChecker.isNewerVersion("1.0.1", "1.0"));
		assertFalse(UpdatesChecker.isNewerVersion("1.0", "1.0.1"));
	}

	@Test
	void testUnparseableVersionIsNotNewer() {
		assertFalse(UpdatesChecker.isNewerVersion("abc", "1.0.7"));
		assertFalse(UpdatesChecker.isNewerVersion("", "1.0.7"));
		assertFalse(UpdatesChecker.isNewerVersion("1.0.9", "xyz"));
	}
}
