/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations;

import org.jetbrains.annotations.*;

import mockit.*;
import mockit.internal.expectations.invocation.*;
import mockit.internal.state.*;
import mockit.internal.util.*;

public final class RecordPhase extends TestOnlyPhase
{
   private final boolean nonStrict;

   RecordPhase(@NotNull RecordAndReplayExecution recordAndReplay, boolean nonStrict)
   {
      super(recordAndReplay);
      this.nonStrict = nonStrict;
   }

   public void addResult(@Nullable Object result)
   {
      getCurrentExpectation().addResult(result);
   }

   public void addSequenceOfReturnValues(@Nullable Object firstValue, @Nullable Object[] remainingValues)
   {
      getCurrentExpectation().addSequenceOfReturnValues(firstValue, remainingValues);
   }

   @Nullable @Override
   Object handleInvocation(
      @Nullable Object mock, int mockAccess, @NotNull String mockClassDesc, @NotNull String mockNameAndDesc,
      @Nullable String genericSignature, boolean withRealImpl, @NotNull Object[] args)
      throws Throwable
   {
      //noinspection AssignmentToMethodParameter
      mock = configureMatchingOnMockInstanceIfSpecified(mock);

      ExpectedInvocation invocation = new ExpectedInvocation(
         mock, mockAccess, mockClassDesc, mockNameAndDesc, matchInstance, genericSignature, args);
      Class<?> callerClass = invocation.getCallerClass();

      if (!Expectations.class.isAssignableFrom(callerClass)) {
         String kind = mockNameAndDesc.charAt(0) == '<' ? "constructor" : "method";
         throw new IllegalStateException(
            "Attempted to record invocation to mocked " + kind + " from outside expectation block" + invocation);
      }

      ExecutingTest executingTest = TestRun.getExecutingTest();
      boolean nonStrictInvocation =
         nonStrict || executingTest.isNonStrictInvocation(mock, mockClassDesc, mockNameAndDesc);

      if (!nonStrictInvocation) {
         executingTest.addStrictMock(mock, matchInstance ? null : mockClassDesc);
      }

      currentExpectation = new Expectation(this, invocation, nonStrictInvocation);

      if (argMatchers != null) {
         invocation.arguments.setMatchers(argMatchers);
         argMatchers = null;
      }

      recordAndReplay.executionState.addExpectation(currentExpectation, nonStrictInvocation);

      return invocation.getDefaultValueForReturnType(this);
   }

   @Nullable private Object configureMatchingOnMockInstanceIfSpecified(@Nullable Object mock)
   {
      matchInstance = false;

      if (mock == null || nextInstanceToMatch == null) {
         return mock;
      }

      Object specified = nextInstanceToMatch;

      if (mock != specified) {
         Class<?> mockedClass = GeneratedClasses.getMockedClass(mock);

         if (!mockedClass.isInstance(specified)) {
            return mock;
         }
      }

      nextInstanceToMatch = null;
      matchInstance = true;
      return specified;
   }

   @Override
   public void handleInvocationCountConstraint(int minInvocations, int maxInvocations)
   {
      int lowerLimit = minInvocations;
      int upperLimit = maxInvocations;

      if (numberOfIterations > 1 && nonStrict) {
         lowerLimit *= numberOfIterations;
         upperLimit *= numberOfIterations;
      }

      getCurrentExpectation().constraints.setLimits(lowerLimit, upperLimit);
   }

   @Override
   public void setCustomErrorMessage(@Nullable CharSequence customMessage)
   {
      getCurrentExpectation().setCustomErrorMessage(customMessage);
   }
}
