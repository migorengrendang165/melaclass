from flask import Flask, request, jsonify
import os
import logging
import tensorflow as tf
from tensorflow.keras.models import load_model
from tensorflow.keras.preprocessing.image import load_img, img_to_array
import numpy as np

app = Flask(__name__)

from tensorflow.keras.losses import SparseCategoricalCrossentropy

class CustomSparseCategoricalCrossentropy(SparseCategoricalCrossentropy):
    def __init__(self, reduction='auto', name='sparse_categorical_crossentropy', from_logits=False, ignore_class=None):
        super().__init__(reduction=reduction, name=name, from_logits=from_logits)

# Custom deserialization function
def custom_deserialize_loss(config, custom_objects=None):
    if config['class_name'] == 'SparseCategoricalCrossentropy':
        # Remove the unexpected 'fn' argument
        config['config'].pop('fn', None)
        return CustomSparseCategoricalCrossentropy(
            reduction=config['config'].get('reduction', 'auto'),
            name=config['config'].get('name', 'sparse_categorical_crossentropy'),
            from_logits=config['config'].get('from_logits', False),
            ignore_class=config['config'].get('ignore_class', None)
        )
    return deserialize(config, custom_objects)

# Custom objects mapping
custom_objects = {
    'SparseCategoricalCrossentropy': custom_deserialize_loss
}

# Load your pre-trained model
model = load_model('cnn_fc_model_full.h5', custom_objects=custom_objects)

# Define class names
class_names = ['actinic keratosis', 'basal cell carcinoma', 'dermatofibroma', 'melanoma', 'nevus', 'pigmented benign keratosis', 'seborrheic keratosis', 'squamous cell carcinoma', 'vascular lesion']

# Function to preprocess the image
def preprocess_image(image_path):
    img = load_img(image_path, target_size=(180, 180))  # adjust target size according to your model
    img = img_to_array(img)
    img = np.expand_dims(img, axis=0)
    img = img / 255.0
    return img

# Endpoint to handle image upload
@app.route('/upload', methods=['POST'])
def upload_file():
    if 'image' not in request.files:
        return jsonify({'error': 'No image provided'}), 400

    image = request.files['image']

    if image.filename == '':
        return jsonify({'error': 'No image selected'}), 400

    if image:
        # Save the image to a folder named 'uploads'
        image.save(os.path.join('uploads', image.filename))
        
        # Preprocess the image
        img = preprocess_image(os.path.join('uploads', image.filename))
        
        # Print the shape of the preprocessed image
        print(f"Preprocessed image shape: {img.shape}")
        
        # Make prediction
        prediction = model.predict(img)
        print(f"Prediction raw output: {prediction}")
        predicted_class = np.argmax(prediction, axis=1)[0]
        predicted_class_name = class_names[predicted_class]
        predicted_probabilities = prediction[0].tolist()
        
        print(f"Predicted class index: {predicted_class}")
        print(f"Predicted class name: {predicted_class_name}")
        
        return jsonify({'message': '[sv] Image uploaded successfully', 
                        'filename': image.filename, 
                        'predicted_class': predicted_class_name,
                       'predicted_probabilities': dict(zip(class_names, predicted_probabilities))}), 200

if __name__ == '__main__':
    # Create the 'uploads' folder if it doesn't exist
    if not os.path.exists('uploads'):
        os.makedirs('uploads')

    # Run the Flask app on localhost:5000
    #app.run(debug=True)
    app.run(host='0.0.0.0', port=8000)
