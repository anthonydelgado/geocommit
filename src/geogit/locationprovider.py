
class LocationProvider(object):
    """ Base class for all location providers which share
    """
    def __init__(self):
        self.name = None

    def get_location(self):
        """ Retrieves a location using this LocationProvider.

        Should be overwritten in specialisations of this class.
        """
        return None


if __name__ == "__main__":
    import doctest
    doctest.testmod()
