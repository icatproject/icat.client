from setuptools import setup

from codecs import open
from os import path

here = path.abspath(path.dirname(__file__))

with open(path.join(here, "..", "..", "..", 'README.md'), encoding='utf-8') as f:
    long_description = f.read()
    
setup(
    name='icat',

    version='4.10.0-SNAPSHOT',

    description='A sample Python project',
    long_description=long_description,

    url='https://icatproject.org',

    author='Steve Fisher on behalf of The ICAT Collaboration',
    author_email='dr.s.m.fisher@gmail.com',

    license='Apache Version 2',
    
    py_modules=["icat"],

    install_requires=['requests_toolbelt']
) 
      
