package com.bitsofproof.supernode.test;

import java.util.List;

import org.jbehave.core.annotations.Configure;
import org.jbehave.core.annotations.UsingEmbedder;
import org.jbehave.core.annotations.UsingSteps;
import org.jbehave.core.annotations.spring.UsingSpring;
import org.jbehave.core.embedder.Embedder;
import org.jbehave.core.io.CodeLocations;
import org.jbehave.core.io.StoryFinder;
import org.jbehave.core.junit.JUnitStories;
import org.jbehave.core.junit.spring.SpringAnnotatedEmbedderRunner;
import org.junit.runner.RunWith;

@RunWith (SpringAnnotatedEmbedderRunner.class)
@Configure
@UsingEmbedder (embedder = Embedder.class, generateViewAfterStories = false, ignoreFailureInStories = false, ignoreFailureInView = false, stepsFactory = true)
@UsingSpring (resources = "classpath:context/stories.xml")
@UsingSteps
public class StoriesRunner extends JUnitStories
{
	@Override
	protected List<String> storyPaths ()
	{
		return new StoryFinder ().findPaths (CodeLocations.codeLocationFromPath ("src/test/resources"), "*.story", "");
	}
}
