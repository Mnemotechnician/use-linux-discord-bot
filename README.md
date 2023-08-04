# "Use Linux" discord bot
Sends a random ai-generated message that is supposed to tell you to use Linux every 4 hours. I am not responsible for any nonsense it may generate.

You can use the `/add-channel` command to opt-in, `/remove-channel` to opt-out.

You can invite the bot to your guild with via the following link:
https://discord.com/api/oauth2/authorize?client_id=1102611086756290560&permissions=68608&scope=bot

# Message generator
This bot uses a recurrent neural network model which generates text character-by-character.
For obvious reasons, you must train the said model before it can be used (see below).

## Preparation
- Clone the repository with `git clone --depth 1 https://github.com/mnemotechnician/use-linux-discord-bot use-linux`
    and navigate to the directory.
- Run `pipenv lock` to refresh the dependencies and also ensure you have pipenv in installed and in you $PATH
    as the generator depends on it.

## Training the model
The module `message-generator-dl` includes a CLI tool that allows you to train the model on your machine.
It uses two datasets:
- `train-learning.txt` contains random phrases meant to make the network learn relationships
between words and occasionally phrases.
During training, 33% phrases in it are postfixed with a message terminator character,
the rest is postfixed with a space.
- `train-main` - contains actual phrases telling people to use Linux. During training,
all messages here are prefixed with a starting token
and postfixed with a message terminator.

In order to train the model, you must first compile the module:
```shell
./gradlew :message-generator-dl:jar
```
After that, you can run the cli tool (assuming you have a jdk installed and configured):
```shell
java -jar message-generator-dl/builds/libs/message-generator-*.jar
```

After launching the tool, you need to type a letter to choose the mode:
- Typing 't' starts the training process.
You will be prompted a few more times for training configuration.
After that, a training process will begin. It may take several hours or more.
- Typing 'g' starts the generation mode,
In which you can use the model to generate messages (you can provide custom starting phrases).
Obviously, this requires you to train a model first.

During the training the model is saved once per 10 training epochs (1 superepoch).
Its files are stored in the directory `~/use-linux/deepl/`. 
You can transfer the contents of this directory to the same folder on a different machine
and use it there if you need to.

# Hosting the bot
Before you can launch the bot, you need to compile the project:
```shell
./gradlew jar
```
Additionally, you need to create a `.env` file and provide the token of your discord bot there.
The file must be located in the same directory from which you're going to run the bot.
```shell
echo "TOKEN=YOUR_DISCORD_BOT_TOKEN_HERE" > .env
```
After that, you can simply launch the bot:
```shell
java -jar app/build/libs/app.jar
```

In order for the main functionality of the bot to work, you must train the message generator model first.
See the "training" section.
