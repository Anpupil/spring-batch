/*
 * Copyright 2006-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.core.step.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.UnexpectedJobExecutionException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.step.AbstractStep;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.util.Assert;

/**
 * A {@link Step} that delegates to a {@link Job} to do its work. This is a
 * great tool for managing dependencies between jobs, and also to modularise
 * complex step logic into something that is testable in isolation. The job is
 * executed with parameters that can be extracted from the step execution, hence
 * this step can also be usefully used as the worker in a parallel or
 * partitioned execution.
 * 
 * @author Dave Syer
 * 
 */
public class JobStep extends AbstractStep {

	/**
	 * The key for the job parameters in the step execution context. Needed for
	 * restarts.
	 */
	private static final String JOB_PARAMETERS_KEY = JobStep.class.getName() + ".JOB_PARAMETERS";

	private Job job;

	private JobLauncher jobLauncher;

	private JobParametersExtractor jobParametersExtractor = new DefaultJobParametersExtractor();

	@Override
	public void afterPropertiesSet() throws Exception {
		super.afterPropertiesSet();
		Assert.state(jobLauncher != null, "A JobLauncher must be provided");
		Assert.state(job != null, "A Job must be provided");
	}

	/**
	 * The {@link Job} to delegate to in this step.
	 * 
	 * @param job a {@link Job}
	 */
	public void setJob(Job job) {
		this.job = job;
	}

	/**
	 * A {@link JobLauncher} is required to be able to run the enclosed
	 * {@link Job}.
	 * 
	 * @param jobLauncher the {@link JobLauncher} to set
	 */
	public void setJobLauncher(JobLauncher jobLauncher) {
		this.jobLauncher = jobLauncher;
	}

	/**
	 * The {@link JobParametersExtractor} is used to extract
	 * {@link JobParametersExtractor} from the {@link StepExecution} to run the
	 * {@link Job}. By default an instance will be provided that simply copies
	 * the {@link JobParameters} from the parent job.
	 * 
	 * @param jobParametersExtractor the {@link JobParametersExtractor} to set
	 */
	public void setJobParametersExtractor(JobParametersExtractor jobParametersExtractor) {
		this.jobParametersExtractor = jobParametersExtractor;
	}

	/**
	 * Execute the job provided by delegating to the {@link JobLauncher} to
	 * prevent duplicate executions. The job parameters will be generated by the
	 * {@link JobParametersExtractor} provided (if any), otherwise empty. On a
	 * restart, the job parameters will be the same as the last (failed)
	 * execution.
	 * 
	 * @see AbstractStep#doExecute(StepExecution)
	 */
	@Override
	protected void doExecute(StepExecution stepExecution) throws Exception {

		ExecutionContext executionContext = stepExecution.getExecutionContext();

		executionContext.put(STEP_TYPE_KEY, this.getClass().getName());

		JobParameters jobParameters;
		if (executionContext.containsKey(JOB_PARAMETERS_KEY)) {
			jobParameters = (JobParameters) executionContext.get(JOB_PARAMETERS_KEY);
		}
		else {
			jobParameters = jobParametersExtractor.getJobParameters(job, stepExecution);
			executionContext.put(JOB_PARAMETERS_KEY, jobParameters);
		}

		JobExecution jobExecution = jobLauncher.run(job, jobParameters);
		if (jobExecution.getStatus().isUnsuccessful()) {
			// AbstractStep will take care of the step execution status
			throw new UnexpectedJobExecutionException("Step failure: the delegate Job failed in JobStep.");
		}

	}

}
