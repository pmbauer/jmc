/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The contents of this file are subject to the terms of either the Universal Permissive License
 * v 1.0 as shown at http://oss.oracle.com/licenses/upl
 *
 * or the following license:
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openjdk.jmc.flightrecorder.rules.jdk.general;

import static org.openjdk.jmc.common.unit.UnitLookup.PLAIN_TEXT;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.IItemFilter;
import org.openjdk.jmc.common.item.ItemFilters;
import org.openjdk.jmc.common.util.IPreferenceValueProvider;
import org.openjdk.jmc.common.util.TypedPreference;
import org.openjdk.jmc.flightrecorder.jdk.JdkAttributes;
import org.openjdk.jmc.flightrecorder.jdk.JdkTypeIDs;
import org.openjdk.jmc.flightrecorder.rules.IRule;
import org.openjdk.jmc.flightrecorder.rules.Result;
import org.openjdk.jmc.flightrecorder.rules.jdk.messages.internal.Messages;
import org.openjdk.jmc.flightrecorder.rules.util.JfrRuleTopics;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit.EventAvailability;
import org.owasp.encoder.Encode;

public class PasswordsInEnvironmentRule implements IRule {
	private static final String PWD_RESULT_ID = "PasswordsInEnvironment"; //$NON-NLS-1$

	public static final TypedPreference<String> EXCLUDED_STRINGS_REGEXP = new TypedPreference<>(
			"passwordsinenvironment.string.exclude.regexp", //$NON-NLS-1$
			Messages.getString(Messages.PasswordsInEnvironmentRuleFactory_CONFIG_EXCLUDED_STRINGS),
			Messages.getString(Messages.PasswordsInEnvironmentRuleFactory_CONFIG_EXCLUDED_STRINGS_LONG),
			PLAIN_TEXT.getPersister(), "(passworld|passwise)"); //$NON-NLS-1$

	private static final List<TypedPreference<?>> CONFIG_ATTRIBUTES = Arrays
			.<TypedPreference<?>> asList(EXCLUDED_STRINGS_REGEXP);

	private Result getResult(IItemCollection items, IPreferenceValueProvider valueProvider) {
		EventAvailability eventAvailability = RulesToolkit.getEventAvailability(items, JdkTypeIDs.ENVIRONMENT_VARIABLE);
		if (eventAvailability != EventAvailability.AVAILABLE) {
			return RulesToolkit.getEventAvailabilityResult(this, items, eventAvailability,
					JdkTypeIDs.ENVIRONMENT_VARIABLE);
		}

		String stringExcludeRegexp = valueProvider.getPreferenceValue(EXCLUDED_STRINGS_REGEXP).trim();
		if (!stringExcludeRegexp.isEmpty()) {
			IItemFilter matchesExclude = ItemFilters.matches(JdkAttributes.ENVIRONMENT_KEY, stringExcludeRegexp);
			IItemFilter stringsExcludingExclude = ItemFilters.and(ItemFilters.type(JdkTypeIDs.ENVIRONMENT_VARIABLE),
					ItemFilters.not(matchesExclude));
			items = items.apply(stringsExcludingExclude);
		}
		// FIXME: Should extract set of variable names instead of joined string
		String pwds = RulesToolkit.findMatches(JdkTypeIDs.ENVIRONMENT_VARIABLE, items, JdkAttributes.ENVIRONMENT_KEY,
				PasswordsInArgumentsRule.PASSWORD_MATCH_STRING, true);
		if (pwds != null && pwds.length() > 0) {
			String[] envs = pwds.split(", "); //$NON-NLS-1$
			StringBuffer passwords = new StringBuffer("<ul>"); //$NON-NLS-1$
			for (String env : envs) {
				passwords.append("<li>"); //$NON-NLS-1$
				passwords.append(Encode.forHtml(env));
				passwords.append("</li>"); //$NON-NLS-1$
			}
			passwords.append("</ul>"); //$NON-NLS-1$
			pwds = passwords.toString();
			String message = MessageFormat
					.format(Messages.getString(Messages.PasswordsInEnvironmentRuleFactory_TEXT_INFO_LONG), pwds);
			if (!stringExcludeRegexp.isEmpty()) {
				message = message + " "
						+ MessageFormat.format(
								Messages.getString(Messages.PasswordsInEnvironmentRuleFactory_TEXT_INFO_EXCLUDED_INFO),
								stringExcludeRegexp);
			}
			return new Result(this, 100, Messages.getString(Messages.PasswordsInEnvironmentRuleFactory_TEXT_INFO),
					message);
		}
		return new Result(this, 0, Messages.getString(Messages.PasswordsInEnvironmentRuleFactory_TEXT_OK));
	}

	@Override
	public RunnableFuture<Result> evaluate(final IItemCollection items, final IPreferenceValueProvider valueProvider) {
		FutureTask<Result> evaluationTask = new FutureTask<>(new Callable<Result>() {
			@Override
			public Result call() throws Exception {
				return getResult(items, valueProvider);
			}
		});
		return evaluationTask;
	}

	@Override
	public Collection<TypedPreference<?>> getConfigurationAttributes() {
		return CONFIG_ATTRIBUTES;
	}

	@Override
	public String getId() {
		return PWD_RESULT_ID;
	}

	@Override
	public String getName() {
		return Messages.getString(Messages.PasswordsInEnvironmentRuleFactory_RULE_NAME);
	}

	@Override
	public String getTopic() {
		return JfrRuleTopics.ENVIRONMENT_VARIABLES;
	}
}
