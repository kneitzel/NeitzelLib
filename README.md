# NeitzelLib Maven Project

Small Library with classes that I found helpfully or interesting.

The idea is not to diretly use anything from this library. It is only meant to provide some code which could be a quick start when required inside a project. So simply copy the classes that you need.

## Components

### core

This is the core library that does not have special dependencies like JavaFX.

It contains:
- **inject** Some small, basic Injection Library (Just a quick start)
- **sql** Helper classes to work with SQL in Java

### fx
Library that extends JavaFX or helps with it.

It contains:
- **component** Just a quick start where I experiment with the idea to have JavaFX components which means that we have a View to display a specific Model.
- **injectfx** Injection inside JavaFX, main idea is to use constructor injection on FXML controller to include required Elements.
- **mvvm** The mvvmFX project seems to get no more updates / is no longer maintained. In this area I am simply playing around with some helper classes to make the use of the mvvm pattern easier through generation of ViewModels. **Currently not really useable**  